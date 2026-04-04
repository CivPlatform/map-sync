package gjum.minecraft.mapsync.mod;

import static gjum.minecraft.mapsync.mod.Cartography.chunkTileFromLevel;

import com.mojang.blaze3d.platform.InputConstants;
import gjum.minecraft.mapsync.mod.config.ModConfig;
import gjum.minecraft.mapsync.mod.config.ServerConfig;
import gjum.minecraft.mapsync.mod.data.CatchupChunk;
import gjum.minecraft.mapsync.mod.data.ChunkTile;
import gjum.minecraft.mapsync.mod.data.RegionPos;
import gjum.minecraft.mapsync.mod.net.CloseContext;
import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.SyncClient;
import gjum.minecraft.mapsync.mod.net.SyncClients;
import gjum.minecraft.mapsync.mod.net.UnexpectedPacketException;
import gjum.minecraft.mapsync.mod.net.auth.AuthProcess;
import gjum.minecraft.mapsync.mod.net.packet.ChunkTilePacket;
import gjum.minecraft.mapsync.mod.net.packet.ClientboundChunkTimestampsResponsePacket;
import gjum.minecraft.mapsync.mod.net.packet.ClientboundIdentityRequestPacket;
import gjum.minecraft.mapsync.mod.net.packet.ClientboundRegionTimestampsPacket;
import gjum.minecraft.mapsync.mod.net.packet.ClientboundWelcomePacket;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundCatchupRequestPacket;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundChunkTimestampsRequestPacket;
import it.unimi.dsi.fastutil.objects.Object2LongArrayMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public final class MapSyncMod implements ClientModInitializer {
	private static final Minecraft mc = Minecraft.getInstance();

	public static final Logger logger = LogManager.getLogger(MapSyncMod.class);

	private static MapSyncMod INSTANCE;

	public static ModConfig modConfig;

	public static MapSyncMod getMod() {
		return INSTANCE;
	}

	public static final String MOD_ID = "mapsync";

	public static final Identifier CATEGORY_ID = Identifier.fromNamespaceAndPath(MOD_ID, "general");
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(CATEGORY_ID);

	private static final KeyMapping openGuiKey = new KeyMapping(
			"key.map-sync.openGui",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_COMMA,
			CATEGORY
			//"category.map-sync"
	);

	/**
	 * Tracks state and render thread for current mc dimension.
	 * Never access this directly; always go through `getDimensionState()`.
	 */
	private @Nullable DimensionState dimensionState;

	/**
	 * Tracks configuration for current mc server.
	 * Never access this directly; always go through `getServerConfig()`.
	 */
	private @Nullable ServerConfig serverConfig;

	public MapSyncMod() {
		if (INSTANCE != null) throw new IllegalStateException("Constructor called twice");
		INSTANCE = this;
	}

	@Override
	public void onInitializeClient() {
		KeyBindingHelper.registerKeyBinding(openGuiKey);

		modConfig = ModConfig.load();
		modConfig.saveNow(); // creates the default file if it doesn't exist yet

		ClientTickEvents.START_CLIENT_TICK.register((minecraft) -> {
			try {
				handleTick(minecraft);
			}
			catch (Throwable e) {
				e.printStackTrace();
			}
		});
		SyncClients.initEvents();
	}

	public void handleTick(
		final @NotNull Minecraft minecraft
	) {
		while (openGuiKey.consumeClick()) {
			minecraft.setScreen(new ModGui(minecraft.screen));
		}

		var dimensionState = getDimensionState();
		if (dimensionState != null) dimensionState.onTick();
	}

	public void handleSyncConnection(
		final @NotNull SyncClient client
	) throws Exception {
		client.authState.set(null);
		AuthProcess.sendHandshake(
			client,
			this.getDimensionState()
		);
	}

	public void handleSyncDisconnection(
		final @NotNull SyncClient client,
		final @NotNull CloseContext context
	) {
		client.authState.set(null);
	}

	/// BEWARE: This is called from whatever thread the given SyncClient websocket is using for reads.
	public void handleSyncPacket(
		final @NotNull SyncClient client,
		final @NotNull Packet received
	) throws Exception {
		switch (received) {
			case ChunkTilePacket(ChunkTile chunkTile) -> handleSharedChunk(client, chunkTile);
			case ClientboundIdentityRequestPacket packet -> AuthProcess.handleIdentityRequest(client, packet);
			case ClientboundWelcomePacket packet -> AuthProcess.handleWelcome(client, packet);
			case ClientboundRegionTimestampsPacket packet -> handleRegionTimestamps(client, packet);
			case ClientboundChunkTimestampsResponsePacket packet -> handleCatchupData(client, packet);
			default -> throw new UnexpectedPacketException(received);
		}
	}

	public void handleRespawn(ClientboundRespawnPacket packet) {
		debugLog("handleRespawn");
		// TODO tell sync server to only send chunks for this dimension now
	}

	/**
	 * only null when not connected to a server
	 */
	public @Nullable ServerConfig getServerConfig() {
		final ServerData currentServer = Minecraft.getInstance().getCurrentServer();
		if (currentServer == null) {
			serverConfig = null;
			return null;
		}
		String gameAddress = currentServer.ip;
		if (!gameAddress.contains(":")) gameAddress += ":25565";

		if (serverConfig == null) {
			serverConfig = ServerConfig.load(gameAddress);
		}
		return serverConfig;
	}

	/**
	 * for current dimension
	 */
	public @Nullable DimensionState getDimensionState() {
		if (mc.level == null) return null;
		var serverConfig = getServerConfig();
		if (serverConfig == null) return null;

		if (dimensionState != null && dimensionState.dimension != mc.level.dimension()) {
			shutDownDimensionState();
		}
		if (dimensionState == null || dimensionState.hasShutDown) {
			dimensionState = new DimensionState(serverConfig.gameAddress, mc.level.dimension());
		}
		return dimensionState;
	}

	private void shutDownDimensionState() {
		if (dimensionState != null) {
			dimensionState.shutDown();
			dimensionState = null;
		}
	}

	/**
	 * an entire chunk was received from the mc server;
	 * send it to the map data server right away.
	 */
	public void handleMcFullChunk(int cx, int cz) {
		// TODO batch this up and send multiple chunks at once

		if (mc.level == null) return;
		// TODO disable in nether (no meaningful "surface layer")
		var dimensionState = getDimensionState();
		if (dimensionState == null) return;

		debugLog("received mc chunk: " + cx + "," + cz);

		var chunkTile = chunkTileFromLevel(mc.level, cx, cz);

		// TODO handle journeymap skipping chunks due to rate limiting - probably need mixin on render function
		if (RenderQueue.areAllMapModsMapping()) {
			dimensionState.setChunkTimestamp(chunkTile.chunkPos(), chunkTile.timestamp());
		}
		for (SyncClient client : SyncClients.get().orElseThrow()) {
			client.sendChunkTile(chunkTile);
		}
	}

	/**
	 * part of a chunk changed, and the chunk is likely to change again soon,
	 * so a ChunkTile update is queued, instead of updating instantly.
	 */
	public void handleMcChunkPartialChange(int cx, int cz) {
		// TODO update ChunkTile in a second or so; remember dimension in case it changes til then
	}

	public void handleSyncServerEncryptionSuccess() {
		debugLog("tcp encrypted");
		// TODO tell server our current dimension
	}

	public void handleRegionTimestamps(SyncClient client, ClientboundRegionTimestampsPacket packet) {
		client.authState.requireWelcomed();
		DimensionState dimension = getDimensionState();
		if (dimension == null) return;
		if (!dimension.dimension.identifier().toString().equals(packet.dimension())) {
			return;
		}

		var regionTs = packet.timestamp();

		var regionPos = new RegionPos(regionTs.x(), regionTs.z());
		long oldestChunkTs = dimension.getOldestChunkTsInRegion(regionPos);
		boolean requiresUpdate = regionTs.timestamp() > oldestChunkTs;

		debugLog("region " + regionPos
				+ (requiresUpdate ? " requires update." : " is up to date.")
				+ " oldest client chunk ts: " + oldestChunkTs
				+ ", newest server chunk ts: " + regionTs.timestamp());

		if (requiresUpdate) {
			client.send(new ServerboundChunkTimestampsRequestPacket(packet.dimension(), regionPos));
		}
	}

	public void handleSharedChunk(SyncClient client, ChunkTile chunkTile) {
		client.authState.requireWelcomed();
		debugLog("received shared chunk: " + chunkTile.chunkPos());
		for (SyncClient syncClient : SyncClients.get().orElseThrow()) {
			syncClient.setServerKnownChunkHash(chunkTile.chunkPos(), chunkTile.dataHash());
		}

		var dimensionState = getDimensionState();
		if (dimensionState == null) return;
		dimensionState.processSharedChunk(chunkTile);
	}

	public void handleCatchupData(SyncClient client, ClientboundChunkTimestampsResponsePacket packet) {
		client.authState.requireWelcomed();
		for (CatchupChunk chunk : packet.chunks()) {
			chunk.syncClient = client;
		}
		var dimensionState = getDimensionState();
		if (dimensionState == null) return;
		debugLog("received catchup: " + packet.chunks().size() + " " + client.syncAddress);
		dimensionState.addCatchupChunks(packet.chunks());
	}

	public void requestCatchupData(
		final @NotNull DimensionState dimensionState,
		final List<@NotNull CatchupChunk> chunks
	) {
		if (chunks == null || chunks.isEmpty()) {
			debugLog("not requesting more catchup: null/empty");
			return;
		}
		debugLog("requesting %d more catchup chunks".formatted(
			chunks.size()
		));
		final var catchupChunksBySyncServer = new IdentityHashMap<SyncClient, List<CatchupChunk>>();
		for (final CatchupChunk chunk : chunks) {
			catchupChunksBySyncServer
				.computeIfAbsent(chunk.syncClient, (key) -> new ArrayList<>())
				.add(chunk);
		}
		for (final var byServerEntry : catchupChunksBySyncServer.entrySet()) {
			final SyncClient syncConnection = byServerEntry.getKey();
			final Map<RegionPos, Object2LongMap<ChunkPos>> regionChunkRequests = new HashMap<>();
			for (final CatchupChunk catchupChunk : byServerEntry.getValue()) {
				regionChunkRequests
					.computeIfAbsent(RegionPos.forChunkPos(catchupChunk.chunkPos()), (regionPos) -> new Object2LongArrayMap<>())
					.mergeLong(catchupChunk.chunkPos(), catchupChunk.timestamp(), Math::max);
			}
			for (final var byRegionEntry : regionChunkRequests.entrySet()) {
				final RegionPos regionPos = byRegionEntry.getKey();
				syncConnection.send(new ServerboundCatchupRequestPacket(
					dimensionState.dimension.identifier(),
					(short) regionPos.x(),
					(short) regionPos.z(),
					byRegionEntry.getValue()
				));
			}
		}
	}

	public static void debugLog(String msg) {
		// we could also make use of slf4j's debug() but I don't know how to reconfigure that at runtime based on globalConfig
		if (modConfig.isShowDebugLog()) {
			logger.info(msg);
		}
	}
}
