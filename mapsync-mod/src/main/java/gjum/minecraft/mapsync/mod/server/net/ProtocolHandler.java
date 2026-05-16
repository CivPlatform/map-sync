package gjum.minecraft.mapsync.mod.server.net;

import gjum.minecraft.mapsync.mod.data.CatchupChunk;
import gjum.minecraft.mapsync.mod.data.RegionTimestamp;
import gjum.minecraft.mapsync.mod.deps.websockets.WebSocket;
import gjum.minecraft.mapsync.mod.deps.websockets.exceptions.WebsocketNotConnectedException;
import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.buffers.BufferWriter;
import gjum.minecraft.mapsync.mod.net.packet.ChunkTilePacket;
import gjum.minecraft.mapsync.mod.net.packet.ClientboundChunkTimestampsResponsePacket;
import gjum.minecraft.mapsync.mod.net.packet.ClientboundIdentityRequestPacket;
import gjum.minecraft.mapsync.mod.net.packet.ClientboundRegionTimestampsPacket;
import gjum.minecraft.mapsync.mod.net.packet.ClientboundWelcomePacket;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundCatchupRequestPacket;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundChunkTimestampsRequestPacket;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundDimensionChangePacket;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundHandshakePacket;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundIdentityResponsePacket;
import gjum.minecraft.mapsync.mod.server.MapSyncServerState;
import gjum.minecraft.mapsync.mod.server.db.ChunkTimestamp;
import gjum.minecraft.mapsync.mod.server.db.StoredChunk;
import gjum.minecraft.mapsync.mod.server.net.auth.MojangSessionAuth;
import gjum.minecraft.mapsync.mod.server.net.auth.OfflineUuid;
import gjum.minecraft.mapsync.mod.server.net.auth.ServerAuthState;
import gjum.minecraft.mapsync.mod.utils.MagicValues;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Java port of `mapsync-server/src/main.ts` (ProtocolHandler). Owns the
/// packet-routing switch, the auth state machine transitions, the chunk
/// relay loop, and the database calls. Stateless across connections —
/// per-client state lives on {@link WsServerClient}.
///
/// Mojang's hasJoined call blocks on an HTTPS round-trip, so it runs on a
/// small daemon executor instead of the websocket worker that delivered
/// the identity response — otherwise a slow Mojang would stall every other
/// connection sharing that worker.
public final class ProtocolHandler {
	private static final Logger logger = LoggerFactory.getLogger(ProtocolHandler.class);
	private static final int SERVER_SALT_LENGTH = 32;

	private final @NotNull MapSyncServerState state;
	private final @NotNull SecureRandom random = new SecureRandom();
	private final @NotNull MojangSessionAuth mojang = new MojangSessionAuth();
	private final @NotNull ExecutorService authExecutor = Executors.newFixedThreadPool(
		4,
		(runnable) -> {
			final Thread t = new Thread(runnable, "MapSync-Auth");
			t.setDaemon(true);
			return t;
		}
	);
	private final @NotNull AtomicReference<MapSyncWsServer> server = new AtomicReference<>();

	public ProtocolHandler(
		final @NotNull MapSyncServerState state
	) {
		this.state = state;
	}

	public void install(
		final @NotNull MapSyncWsServer server
	) {
		this.server.set(server);
	}

	public void shutdown() {
		this.authExecutor.shutdownNow();
	}

	// ============================================================
	// Connection lifecycle
	// ============================================================

	public void handleClientConnected(
		@SuppressWarnings("unused") final @NotNull WsServerClient client
	) {
		// The client's WsServerClient constructor already seeds the auth state
		// to AwaitingHandshake — nothing more to do until the first packet.
	}

	public void handleClientDisconnected(
		final @NotNull WsServerClient client
	) {
		client.auth = new ServerAuthState.AwaitingHandshake();
	}

	// ============================================================
	// Packet dispatch
	// ============================================================

	public void handleClientPacket(
		final @NotNull WsServerClient client,
		final @NotNull Packet packet
	) throws Exception {
		switch (packet) {
			case final ServerboundHandshakePacket p -> handleHandshake(client, p);
			case final ServerboundIdentityResponsePacket p -> handleIdentityResponse(client, p);
			case final ServerboundDimensionChangePacket p -> handleDimensionChange(client, p);
			case final ServerboundChunkTimestampsRequestPacket p -> handleChunkTimestampsRequest(client, p);
			case final ServerboundCatchupRequestPacket p -> handleCatchupRequest(client, p);
			default -> client.kick("unexpected packet: " + packet.getClass().getSimpleName());
		}
	}

	public void handleChunkTileFrame(
		final @NotNull WsServerClient client,
		final @NotNull ServerChunkTileFrame frame
	) {
		if (!(client.auth instanceof final ServerAuthState.Welcomed welcomed)) {
			client.kick("chunk-tile before welcome");
			return;
		}
		if (!client.isInDimension(frame.dimension())) {
			logger.warn(
				"[{}] sent chunk for {} while in {}",
				client.name(), frame.dimension(), client.dimension
			);
			return;
		}
		try {
			this.state.database().storeChunkData(
				frame.dimension().toString(),
				frame.chunkX(), frame.chunkZ(),
				welcomed.uuid(),
				frame.timestamp(),
				frame.dataVersion(),
				frame.dataHash(),
				frame.columnBytes()
			);
		}
		catch (final Exception e) {
			logger.warn("[{}] failed to persist chunk ({},{})", client.name(), frame.chunkX(), frame.chunkZ(), e);
		}
		relayChunkTile(client, frame.wireBytes());
	}

	private void relayChunkTile(
		final @NotNull WsServerClient sender,
		final byte @NotNull [] wireBytes
	) {
		final MapSyncWsServer srv = this.server.get();
		if (srv == null) return;
		for (final WebSocket conn : srv.connections()) {
			final WsServerClient other = conn.getAttachment();
			if (other == null || other == sender || !other.isWelcomed()) continue;
			try {
				conn.send(wireBytes);
			}
			catch (final WebsocketNotConnectedException ignored) {
				// Close race — drop the relay silently.
			}
			catch (final Exception e) {
				logger.warn("[{}] relay send failed", other.name(), e);
			}
		}
	}

	// ============================================================
	// Auth
	// ============================================================

	private void handleHandshake(
		final @NotNull WsServerClient client,
		final @NotNull ServerboundHandshakePacket packet
	) {
		if (!(client.auth instanceof ServerAuthState.AwaitingHandshake)) {
			client.kick("handshake out of order");
			return;
		}
		if (!MagicValues.VERSION.equals(packet.modVersion())) {
			client.kick("unsupported mod version: " + packet.modVersion());
			return;
		}
		client.gameAddress = packet.gameAddress().address();
		final byte[] serverSalt;
		if (this.state.config().auth) {
			serverSalt = new byte[SERVER_SALT_LENGTH];
			this.random.nextBytes(serverSalt);
		}
		else {
			serverSalt = new byte[0];
		}
		client.auth = new ServerAuthState.AwaitingIdentityResponse(serverSalt);
		client.send(new ClientboundIdentityRequestPacket(serverSalt));
	}

	private void handleIdentityResponse(
		final @NotNull WsServerClient client,
		final @NotNull ServerboundIdentityResponsePacket packet
	) {
		if (!(client.auth instanceof final ServerAuthState.AwaitingIdentityResponse waiting)) {
			client.kick("identity-response out of order");
			return;
		}
		if (this.state.config().auth) {
			if (packet.clientSalt().length == 0) {
				client.kick("client sent empty salt despite auth required");
				return;
			}
			this.authExecutor.execute(() -> doMojangAuth(
				client, packet.claimedUsername(), waiting.serverSalt(), packet.clientSalt()
			));
			return;
		}
		if (packet.clientSalt().length != 0) {
			client.kick("client sent non-empty salt despite auth disabled");
			return;
		}
		final UUID offlineUuid = OfflineUuid.forName(packet.claimedUsername());
		client.auth = new ServerAuthState.Welcomed(packet.claimedUsername(), offlineUuid, false);
		finishAuthSuccess(client);
	}

	private void doMojangAuth(
		final @NotNull WsServerClient client,
		final @NotNull String claimedUsername,
		final byte @NotNull [] serverSalt,
		final byte @NotNull [] clientSalt
	) {
		try {
			final MojangSessionAuth.Result result = this.mojang.hasJoined(claimedUsername, serverSalt, clientSalt);
			// Re-check state in case the connection moved on / closed mid-flight.
			if (!(client.auth instanceof ServerAuthState.AwaitingIdentityResponse)) {
				logger.info("[{}] auth result arrived but state moved on", client.name());
				return;
			}
			client.auth = new ServerAuthState.Welcomed(result.name(), result.uuid(), true);
			finishAuthSuccess(client);
		}
		catch (final Exception e) {
			logger.warn("[{}] Mojang auth failed", client.name(), e);
			client.kick("auth failed: " + e.getMessage());
		}
	}

	private void finishAuthSuccess(
		final @NotNull WsServerClient client
	) {
		final ServerAuthState.Welcomed welcomed = (ServerAuthState.Welcomed) client.auth;
		if (welcomed.authed()) {
			try {
				this.state.uuidCache().put(welcomed.name(), welcomed.uuid());
				this.state.uuidCache().save(this.state.dataDir().resolve("uuid_cache.json"));
			}
			catch (final Exception e) {
				logger.warn("[{}] failed to cache uuid", client.name(), e);
			}
		}
		if (this.state.config().whitelist && !this.state.whitelist().isWhitelisted(welcomed.uuid())) {
			client.kick("not whitelisted");
			return;
		}
		client.send(new ClientboundWelcomePacket());
	}

	// ============================================================
	// Dimension + sync
	// ============================================================

	private void handleDimensionChange(
		final @NotNull WsServerClient client,
		final @NotNull ServerboundDimensionChangePacket packet
	) {
		if (!client.isWelcomed()) {
			client.kick("dimension-change before welcome");
			return;
		}
		if (packet.dimension().equals(client.dimension)) {
			return;
		}
		client.dimension = packet.dimension();
		final String dimensionString = packet.dimension().toString();
		try {
			final List<RegionTimestamp> regions = this.state.database().getRegionTimestamps(dimensionString);
			for (final RegionTimestamp region : regions) {
				client.send(new ClientboundRegionTimestampsPacket(dimensionString, region));
			}
		}
		catch (final Exception e) {
			logger.warn("[{}] failed to send region timestamps", client.name(), e);
		}
	}

	private void handleChunkTimestampsRequest(
		final @NotNull WsServerClient client,
		final @NotNull ServerboundChunkTimestampsRequestPacket packet
	) {
		if (!client.isWelcomed()) {
			client.kick("chunk-timestamps-request before welcome");
			return;
		}
		final Identifier dim = Identifier.parse(packet.dimension());
		if (!client.isInDimension(dim)) {
			logger.warn(
				"[{}] requested chunk timestamps for {} while in {}",
				client.name(), dim, client.dimension
			);
			return;
		}
		final ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dim);
		try {
			final List<ChunkTimestamp> rows = this.state.database()
				.getChunkTimestamps(packet.dimension(), packet.region().x(), packet.region().z());
			if (rows.isEmpty()) return;
			final List<CatchupChunk> catchupChunks = rows.stream()
				.map((row) -> new CatchupChunk(dimKey, row.chunkX(), row.chunkZ(), row.timestamp()))
				.toList();
			client.send(new ClientboundChunkTimestampsResponsePacket(catchupChunks));
		}
		catch (final Exception e) {
			logger.warn("[{}] failed to fetch chunk timestamps", client.name(), e);
		}
	}

	private void handleCatchupRequest(
		final @NotNull WsServerClient client,
		final @NotNull ServerboundCatchupRequestPacket packet
	) {
		if (!client.isWelcomed()) {
			client.kick("catchup-request before welcome");
			return;
		}
		if (!client.isInDimension(packet.dimension())) {
			logger.warn(
				"[{}] requested catchup for {} while in {}",
				client.name(), packet.dimension(), client.dimension
			);
			return;
		}
		final String dimensionString = packet.dimension().toString();
		for (final Map.Entry<ChunkPos, Long> entry : packet.chunks().entrySet()) {
			final ChunkPos pos = entry.getKey();
			final long requestedTs = entry.getValue();
			final Optional<StoredChunk> stored;
			try {
				stored = this.state.database().getChunkData(dimensionString, pos.x(), pos.z());
			}
			catch (final Exception e) {
				logger.warn("[{}] catchup db lookup failed for ({},{})", client.name(), pos.x(), pos.z(), e);
				continue;
			}
			if (stored.isEmpty()) {
				logger.info("[{}] requested unavailable chunk ({},{})", client.name(), pos.x(), pos.z());
				continue;
			}
			// Mirror the TS server's strict timestamp match: if our copy is
			// newer the client should already be receiving a relay, and if
			// our copy is older the client already has a newer one.
			if (stored.get().timestamp() != requestedTs) continue;
			sendRawChunkTilePacket(client, dimensionString, pos.x(), pos.z(), stored.get());
		}
	}

	/// Emits a ChunkTilePacket wire form constructed directly from stored
	/// metadata + raw column bytes, bypassing the BlockColumn parser (which
	/// requires Minecraft.getInstance().level — client-only). The byte
	/// sequence matches what `ChunkTile.write` would produce.
	private void sendRawChunkTilePacket(
		final @NotNull WsServerClient client,
		final @NotNull String dimensionString,
		final int chunkX,
		final int chunkZ,
		final @NotNull StoredChunk stored
	) {
		final byte[] bytes;
		try {
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			final BufferWriter w = new BufferWriter(out);
			w.writeUnt8(ChunkTilePacket.PACKET_ID);
			w.writeString(dimensionString);
			w.writeInt32(chunkX);
			w.writeInt32(chunkZ);
			w.writeInt64(stored.timestamp());
			w.writeUnt16(stored.dataVersion());
			w.writeBytes(stored.hash());
			w.writeBytes(stored.data());
			bytes = out.toByteArray();
		}
		catch (final Exception e) {
			logger.warn("[{}] failed to encode catchup chunk ({},{})", client.name(), chunkX, chunkZ, e);
			return;
		}
		client.sendRaw(bytes);
	}
}
