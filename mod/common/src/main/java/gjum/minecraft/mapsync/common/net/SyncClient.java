package gjum.minecraft.mapsync.common.net;

import com.mojang.authlib.exceptions.AuthenticationException;
import gjum.minecraft.mapsync.common.MapSyncMod;
import gjum.minecraft.mapsync.common.data.CatchupChunk;
import gjum.minecraft.mapsync.common.data.ChunkTile;
import gjum.minecraft.mapsync.common.net.packet.ChunkTilePacket;
import gjum.minecraft.mapsync.common.net.packet.ClientboundChunkTimestampsResponsePacket;
import gjum.minecraft.mapsync.common.net.packet.ClientboundAuthRequestPacket;
import gjum.minecraft.mapsync.common.net.packet.ClientboundRegionTimestampsPacket;
import gjum.minecraft.mapsync.common.net.packet.ClientboundWelcomePacket;
import gjum.minecraft.mapsync.common.net.packet.ServerboundCatchupRequestPacket;
import gjum.minecraft.mapsync.common.net.packet.ServerboundChunkTimestampsRequestPacket;
import gjum.minecraft.mapsync.common.net.packet.ServerboundAuthResponsePacket;
import gjum.minecraft.mapsync.common.net.packet.ServerboundHandshakePacket;
import gjum.minecraft.mapsync.common.utils.Hasher;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.world.level.ChunkPos;
import org.apache.commons.lang3.StringUtils;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * handles reconnection, authentication, encryption
 */
public class SyncClient {
	private final HashMap<ChunkPos, byte[]> serverKnownChunkHashes = new HashMap<>();

	public synchronized void sendChunkTile(ChunkTile chunkTile) {
		var serverKnownHash = getServerKnownChunkHash(chunkTile.chunkPos());
		if (Arrays.equals(chunkTile.dataHash(), serverKnownHash)) {
			MapSyncMod.debugLog("server already has chunk (hash) " + chunkTile.chunkPos());
			return; // server already has this chunk
		}

		send(new ChunkTilePacket(chunkTile));

		// assume packet will reach server eventually
		setServerKnownChunkHash(chunkTile.chunkPos(), chunkTile.dataHash());
	}

	public synchronized byte[] getServerKnownChunkHash(ChunkPos chunkPos) {
		return serverKnownChunkHashes.get(chunkPos);
	}

	public synchronized void setServerKnownChunkHash(ChunkPos chunkPos, byte[] hash) {
		serverKnownChunkHashes.put(chunkPos, hash);
	}

	// XXX end of hotfix

	public static final Logger LOGGER = LoggerFactory.getLogger(SyncClient.class);
	public static final int RESTART_DELAY = 5;

	public final @NotNull SyncAddress syncAddress;
	public final @NotNull String gameAddress;

	/**
	 * false = don't auto-reconnect but maintain connection as long as it stays up.
	 * can be set to true again later.
	 */
	public boolean autoReconnect = true;
	/**
	 * false = don't reconnect under any circumstances,
	 * and disconnect when coming across this during a check
	 */
	public boolean isShutDown = false;
	private @Nullable String lastError;
	/**
	 * limited (on insert) to 199 entries
	 */
	private final ArrayList<Packet> queue = new ArrayList<>();
	private final SyncConnection connection;
	/** Whether the connection has survived the handshake and login exchange */
	private boolean isEstablished = false;

	public SyncClient(
		final @NotNull SyncAddress syncAddress,
		final @NotNull String gameAddress
	) {
		this.syncAddress = Objects.requireNonNull(syncAddress);
		this.gameAddress = Objects.requireNonNull(gameAddress);
		this.connection = new SyncConnection(syncAddress);
		this.connection.connect();
	}

	private class SyncConnection extends WebSocketClient {
		public SyncConnection(
			final @NotNull SyncAddress serverUri
		) {
			super(serverUri.address());
		}
		@Override
		public void onOpen(
			final @NotNull ServerHandshake handshake
		) {
			LOGGER.info("[map-sync] OPENED!");
			INTERNAL_send(new ServerboundHandshakePacket(
				MapSyncMod.getMod().getVersion(),
				Minecraft.getInstance().getUser().getName(),
				SyncClient.this.gameAddress,
				MapSyncMod.getMod().getDimensionState().dimension.location().toString()
			));
		}
		@Override
		public void onClose(
			final int code,
			final @UnknownNullability String reason,
			final boolean remote
		) {
			LOGGER.info("[map-sync] Closed!");
			SyncClient.this.handleDisconnect(code, reason, remote);
		}
		@Override
		public void onError(
			final @NotNull Exception thrown
		) {
			LOGGER.warn("[map-sync] Something went wrong", thrown);
			SyncClient.this.lastError = thrown.getMessage();
			close();
		}
		@Override
		public void onMessage(
			final @NotNull String message
		) {
			LOGGER.warn("[map-sync] Received a string message from the server!");
			SyncClient.this.lastError = "Server sent unsupported packets!";
			SyncClient.this.autoReconnect = false;
			SyncClient.this.isShutDown = true;
			SyncClient.this.isEstablished = false;
			close();
		}
		@Override
		public void onMessage(
			@NotNull ByteBuffer bytes
		) {
			LOGGER.info("[map-sync] Received bytes!");
			final ByteBuf buf = Unpooled.wrappedBuffer(bytes);
			try {
				final byte packetId = buf.readByte();
				switch (packetId) {
					case ChunkTilePacket.PACKET_ID -> {
						final var packet = (ChunkTilePacket) ChunkTilePacket.read(buf);
						Packet.assertNoRemainder(buf);
						MapSyncMod.getMod().handleSharedChunk(packet.chunkTile);
					}
					case ClientboundAuthRequestPacket.PACKET_ID -> {
						final ClientboundAuthRequestPacket packet = ClientboundAuthRequestPacket.read(buf);
						Packet.assertNoRemainder(buf);
						handleAuthRequest(this, packet);
					}
					case ClientboundWelcomePacket.PACKET_ID -> {
						final ClientboundWelcomePacket packet =  ClientboundWelcomePacket.read(buf);
						Packet.assertNoRemainder(buf);
						handleWelcome(packet);
					}
					case ClientboundChunkTimestampsResponsePacket.PACKET_ID -> {
						final var packet = (ClientboundChunkTimestampsResponsePacket) ClientboundChunkTimestampsResponsePacket.read(buf);
						Packet.assertNoRemainder(buf);
						for (CatchupChunk chunk : packet.chunks) {
							chunk.syncClient = SyncClient.this;
						}
						MapSyncMod.getMod().handleCatchupData(packet);
					}
					case ClientboundRegionTimestampsPacket.PACKET_ID -> {
						final var packet = (ClientboundRegionTimestampsPacket) ClientboundRegionTimestampsPacket.read(buf);
						Packet.assertNoRemainder(buf);
						MapSyncMod.getMod().handleRegionTimestamps(packet, SyncClient.this);
					}
				}
			}
			catch (final Exception thrown) {
				onError(thrown);
			}
		}
	}

	public synchronized void connect() {
		if (this.isShutDown) {
			return;
		}
		if (this.connection.getReadyState() == ReadyState.OPEN) {
			this.connection.close();
		}
		this.connection.connect();
	}

	private void handleDisconnect(
		final int code,
		final @UnknownNullability String reason,
		final boolean remote
	) {
		this.isEstablished = false;

		if (Minecraft.getInstance().level == null) {
			this.isShutDown = true;
		}

		if (StringUtils.isNotEmpty(reason)) {
			this.lastError = reason;
		}

		LOGGER.warn("[map-sync] Got disconnected from '{}': {}", this.syncAddress, this.lastError);

		if (!this.isShutDown && this.autoReconnect && !remote) {
			// TODO: Readd auto-reconnect
			// workerGroup.schedule(this::connect, retrySec, TimeUnit.SECONDS);
		}
	}

	public boolean isEstablished() {
		return this.isEstablished;
	}

	public String getError() {
		return lastError;
	}

	/**
	 * Send if encrypted, or queue and send once encryption is set up.
	 */
	public synchronized void send(Packet packet) {
		if (this.connection == null || this.connection.getReadyState() != ReadyState.OPEN) {
			this.queue.add(packet);
			final int queueSize = this.queue.size();
			if (queueSize > 200) {
				final List<Packet> slice = List.copyOf(this.queue.subList(100, queueSize));
				this.queue.clear();
				this.queue.addAll(slice);
			}
			return;
		}
		INTERNAL_send(packet);
	}

	private void INTERNAL_send(
		final @NotNull Packet packet
	) {
		final ByteBuf buf = Unpooled.buffer();
		buf.writeByte(getClientPacketId(packet));
		packet.write(buf);

		final byte[] bytes = new byte[buf.readableBytes()];
		buf.readBytes(bytes);

		this.connection.send(bytes);
	}

	private static int getClientPacketId(Packet packet) {
		if (packet instanceof ChunkTilePacket) return ChunkTilePacket.PACKET_ID;
		if (packet instanceof ServerboundHandshakePacket) return ServerboundHandshakePacket.PACKET_ID;
		if (packet instanceof ServerboundAuthResponsePacket) return ServerboundAuthResponsePacket.PACKET_ID;
		if (packet instanceof ServerboundCatchupRequestPacket) return ServerboundCatchupRequestPacket.PACKET_ID;
		if (packet instanceof ServerboundChunkTimestampsRequestPacket) return ServerboundChunkTimestampsRequestPacket.PACKET_ID;
		throw new IllegalArgumentException("Unknown client packet class " + packet);
	}

	public synchronized void shutDown() {
		this.isShutDown = true;
		this.isEstablished = false;
		this.connection.close();
	}

	private void handleAuthRequest(
		final @NotNull WebSocketClient connection,
		final @NotNull ClientboundAuthRequestPacket packet
	) {
		final var clientSecret = new byte[Long.BYTES];
		ThreadLocalRandom.current().nextBytes(clientSecret);

		// note that this is different from minecraft (we get no negative hashes)
		final String shaHex = HexFormat.of().formatHex(Hasher.sha1()
			.update(clientSecret)
			.update(packet.serverSecret())
			.generateHash()
		);

		final User session = Minecraft.getInstance().getUser();
		try {
			Minecraft.getInstance().getMinecraftSessionService().joinServer(
				session.getGameProfile(),
				session.getAccessToken(),
				shaHex
			);
		}
		catch (final AuthenticationException authenticationFailure) {
			LOGGER.warn("Failed authentication check!");
			connection.close();
			return;
		}

		INTERNAL_send(new ServerboundAuthResponsePacket(
			clientSecret
		));
	}

	private synchronized void handleWelcome(
		final @NotNull ClientboundWelcomePacket packet
	) {
		this.isEstablished = true;
		this.lastError = null;

		for (final Packet pendingPacket : List.copyOf(this.queue)) {
			INTERNAL_send(pendingPacket);
		}
		this.queue.clear();
	}
}
