package gjum.minecraft.mapsync.mod.net;

import static gjum.minecraft.mapsync.mod.MapSyncMod.debugLog;

import gjum.minecraft.mapsync.mod.MapSyncMod;
import gjum.minecraft.mapsync.mod.data.ChunkTile;
import gjum.minecraft.mapsync.mod.net.buffers.BufferReader;
import gjum.minecraft.mapsync.mod.net.buffers.BufferWriter;
import gjum.minecraft.mapsync.mod.net.packet.ChunkTilePacket;
import gjum.minecraft.mapsync.mod.net.packet.ClientboundChunkTimestampsResponsePacket;
import gjum.minecraft.mapsync.mod.net.packet.ClientboundIdentityRequestPacket;
import gjum.minecraft.mapsync.mod.net.packet.ClientboundRegionTimestampsPacket;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundCatchupRequestPacket;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundChunkTimestampsRequestPacket;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundIdentityResponsePacket;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundHandshakePacket;
import gjum.minecraft.mapsync.mod.net.auth.AuthStateHolder;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * handles reconnection, authentication, encryption
 */
public final class SyncClient {
	private final HashMap<ChunkPos, byte[]> serverKnownChunkHashes = new HashMap<>();

	public synchronized void sendChunkTile(ChunkTile chunkTile) {
		var serverKnownHash = getServerKnownChunkHash(chunkTile.chunkPos());
		if (Arrays.equals(chunkTile.dataHash(), serverKnownHash)) {
			debugLog("server already has chunk (hash) " + chunkTile.chunkPos());
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
	private static final AtomicLong lastClientId = new AtomicLong(0L);

	public final long clientId;
	public final URI syncAddress;
	public final String gameAddress;
	public final AuthStateHolder auth = new AuthStateHolder();
	private volatile WebSocket websocket;
	private volatile boolean closing;
	private volatile boolean kicked;

	private SyncClient(
		final @NotNull URI syncAddress,
		final @NotNull String gameAddress
	) {
		this.clientId = lastClientId.incrementAndGet();
		this.syncAddress = Objects.requireNonNull(syncAddress);
		this.gameAddress = Objects.requireNonNull(gameAddress);
		this.websocket = null;
	}

	public static @NotNull SyncClient create(
		final @NotNull MapSyncMod mod,
		final @NotNull HttpClient httpClient,
		final @NotNull String syncAddress,
		final @NotNull String gameAddress
	) {
		return create(
			mod,
			httpClient,
			URI.create(syncAddress),
			gameAddress
		);
	}

	public static @NotNull SyncClient create(
		final @NotNull MapSyncMod mod,
		final @NotNull HttpClient httpClient,
		final @NotNull URI syncAddress,
		final @NotNull String gameAddress
	) {
		Objects.requireNonNull(mod);
		final var client = new SyncClient(syncAddress, gameAddress);
		httpClient.newWebSocketBuilder()
			.buildAsync(syncAddress, new WebSocket.Listener() {
				@Override
				public void onOpen(
					final @NotNull WebSocket ws
				) {
					client.websocket = ws;
					LOGGER.info("[{}] Connected!", client.name());
					try {
						mod.handleSyncConnection(client);
					}
					catch (final Exception e) {
						LOGGER.error("[{}] Error in connection handler!", client.name(), e);
						ws.sendClose(1003, null);
					}
				}
				@Override
				public CompletionStage<?> onText(
					final @NotNull WebSocket ws,
					final @NotNull CharSequence message,
					final boolean last
				) {
					LOGGER.error("[{}] Received a text packet! Closing!", client.name());
					return ws.sendClose(1003, null);
				}
				private final ByteArrayOutputStream accumulate = new ByteArrayOutputStream();
				@Override
				public CompletionStage<?> onBinary(
					final @NotNull WebSocket ws,
					final @NotNull ByteBuffer message,
					final boolean last
				) {
					final byte[] totalReceivedBytes; {
						final var receivedBytes = new byte[message.remaining()];
						message.get(receivedBytes);
						this.accumulate.writeBytes(receivedBytes);
						if (!last) {
							ws.request(1);
							return null;
						}
						totalReceivedBytes = this.accumulate.toByteArray();
						this.accumulate.reset();
					}
					final var reader = new BufferReader(ByteBuffer.wrap(totalReceivedBytes));
					final Packet packet;
					try {
						final int packetId = reader.readUnt8();
						packet = switch (packetId) {
							case ChunkTilePacket.PACKET_ID -> ChunkTilePacket.read(reader);
							case ClientboundIdentityRequestPacket.PACKET_ID -> ClientboundIdentityRequestPacket.read(reader);
							case ClientboundChunkTimestampsResponsePacket.PACKET_ID -> ClientboundChunkTimestampsResponsePacket.read(reader);
							case ClientboundRegionTimestampsPacket.PACKET_ID -> ClientboundRegionTimestampsPacket.read(reader);
							default -> throw new UnexpectedPacketException(packetId);
						};
					}
					catch (final Exception e) {
						LOGGER.error("[{}] Could not decode packet!", client.name(), e);
						ws.sendClose(1002, null);
						return null;
					}
					try {
						mod.handleSyncPacket(client, packet);
					}
					catch (final Exception e) {
						LOGGER.error("[{}] Could not handle packet!", client.name(), e);
						ws.sendClose(1008, null);
					}
					return null;
				}
				@Override
				public CompletionStage<?> onClose(
					final @NotNull WebSocket ws,
					final int statusCode,
					final String reason
				) {
					client.websocket = null;
					client.auth.set(null);
					client.kicked = true;
					LOGGER.info("[{}] Closing! {}: {}", client.name(), statusCode, reason);
					mod.handleSyncDisconnection(client, new CloseReason.Closed(statusCode, reason));
					return null;
				}
				@Override
				public void onError(
					final @NotNull WebSocket ws,
					final @NotNull Throwable thrown
				) {
					client.websocket = null;
					client.auth.set(null);
					LOGGER.error("[{}] Closing on error!", client.name(), thrown);
					mod.handleSyncDisconnection(client, new CloseReason.Error(thrown));
				}
			});
		return client;
	}

	public @NotNull String name() {
		return "Client%d".formatted(this.clientId);
	}

	public boolean kicked() {
		return this.kicked;
	}

	public enum ConnectionState { DISCONNECTED, CONNECTED, AUTHED }
	public @NotNull ConnectionState state() {
		if (!(this.websocket instanceof final WebSocket ws)) {
			return ConnectionState.DISCONNECTED;
		}
		if (ws.isInputClosed() || ws.isOutputClosed()) {
			return ConnectionState.DISCONNECTED;
		}
		if (this.auth.get() == null) {
			return ConnectionState.CONNECTED;
		}
		return ConnectionState.AUTHED;
	}

	public synchronized void send(
		final @NotNull Packet packet
	) {
		Objects.requireNonNull(packet);
		if (!(this.websocket instanceof WebSocket ws)) {
			throw new IllegalStateException("WebSocket not connected");
		}
		if (this.closing) {
			throw new IllegalStateException("WebSocket is closing");
		}
		final var out = new ByteArrayOutputStream();
		try {
			final var writer = new BufferWriter(out);
			writer.writeUnt8(switch (packet) {
				case ChunkTilePacket $ -> ChunkTilePacket.PACKET_ID;
				case ServerboundHandshakePacket $ -> ServerboundHandshakePacket.PACKET_ID;
				case ServerboundIdentityResponsePacket $ -> ServerboundIdentityResponsePacket.PACKET_ID;
				case ServerboundChunkTimestampsRequestPacket $ -> ServerboundChunkTimestampsRequestPacket.PACKET_ID;
				case ServerboundCatchupRequestPacket $ -> ServerboundCatchupRequestPacket.PACKET_ID;
				default -> throw new UnexpectedPacketException(packet);
			});
			packet.write(writer);
		}
		catch (final Exception e) {
			throw new RuntimeException(e);
		}
		ws.sendBinary(ByteBuffer.wrap(out.toByteArray()), true);
	}

	public synchronized void disconnect() {
		if (this.websocket instanceof WebSocket ws) {
			this.websocket = null;
			ws.sendClose(1000, "Disconnecting");
		}
		this.closing = true;
		this.auth.set(null);
	}
}
