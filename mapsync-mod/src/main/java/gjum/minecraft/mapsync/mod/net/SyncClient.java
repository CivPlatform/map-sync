package gjum.minecraft.mapsync.mod.net;

import gjum.minecraft.mapsync.mod.MapSyncMod;
import gjum.minecraft.mapsync.mod.data.ChunkTile;
import gjum.minecraft.mapsync.mod.data.GameAddress;
import gjum.minecraft.mapsync.mod.deps.websockets.client.WebSocketClient;
import gjum.minecraft.mapsync.mod.deps.websockets.drafts.Draft;
import gjum.minecraft.mapsync.mod.deps.websockets.drafts.Draft_6455;
import gjum.minecraft.mapsync.mod.deps.websockets.exceptions.WebsocketNotConnectedException;
import gjum.minecraft.mapsync.mod.deps.websockets.handshake.ServerHandshake;
import gjum.minecraft.mapsync.mod.net.auth.AuthStateHolder;
import gjum.minecraft.mapsync.mod.net.auth.Welcomed;
import gjum.minecraft.mapsync.mod.net.buffers.BufferReader;
import gjum.minecraft.mapsync.mod.net.buffers.BufferWriter;
import gjum.minecraft.mapsync.mod.net.packet.ChunkTilePacket;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// handles reconnection, authentication, encryption
public class SyncClient {
	private final HashMap<ChunkPos, byte[]> serverKnownChunkHashes = new HashMap<>();

	public synchronized void sendChunkTile(ChunkTile chunkTile) {
		if (this.state() != ConnectionState.WELCOMED) {
			return;
		}

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
	private static final AtomicLong LAST_CLIENT_ID = new AtomicLong(0L);
	private static final int MAX_PAYLOAD_LENGTH = (1 << Short.SIZE) - 1;

	public final long clientId;
	public final String syncAddress;
	public final GameAddress gameAddress;

	/// false = don't auto-reconnect but maintain connection as long as it stays up.
	/// can be set to true again later.
	public boolean shouldReconnect = true;
	public volatile String lastError = null;

	public final AuthStateHolder authState = new AuthStateHolder();

	public SyncClient(
		final @NotNull String syncAddress,
		final @NotNull GameAddress gameAddress
	) {
		this.clientId = LAST_CLIENT_ID.incrementAndGet();
		this.syncAddress = Objects.requireNonNull(syncAddress);
		this.gameAddress = Objects.requireNonNull(gameAddress);
		this.websocket = new WsClient(URI.create(syncAddress));
	}

	public @NotNull String name() {
		return "Client%d".formatted(this.clientId);
	}

	public enum ConnectionState { DISCONNECTED, CONNECTED, WELCOMED }
	public synchronized @NotNull ConnectionState state() {
		return switch (this.websocket.getReadyState()) {
			case NOT_YET_CONNECTED, CLOSING, CLOSED -> ConnectionState.DISCONNECTED;
			case OPEN -> switch (this.authState.get()) {
				case final Welcomed $ -> ConnectionState.WELCOMED;
				case null, default -> ConnectionState.CONNECTED;
			};
		};
	}

	@ApiStatus.Internal
	public final WsClient websocket;
	public final class WsClient extends WebSocketClient {
		private WsClient(
			final @NotNull URI syncAddress
		) {
			super(Objects.requireNonNull(syncAddress), createDraft());
			this.setConnectionLostTimeout(30);
			this.setAttachment(SyncClient.this);
		}

		private static @NotNull Draft createDraft() {
			return new Draft_6455();
		}

		@Override
		public void onOpen(
			final @NotNull ServerHandshake handshake
		) {
			LOGGER.info("[{}] Connected to {}", SyncClient.this.name(), this.uri);
			SyncClient.this.lastError = null;
			try {
				MapSyncMod.getMod().handleSyncConnection(SyncClient.this);
			}
			catch (final Exception e) {
				this.onError(e);
			}
		}

		@Override
		public void onClose(
			final int closeCode,
			final String reason,
			final boolean wasKicked
		) {
			LOGGER.info("[{}] Closing... {}:{} (kicked: {})", SyncClient.this.name(), closeCode, reason, wasKicked);
			if (wasKicked) {
				SyncClient.this.shouldReconnect = false;
			}
			SyncClient.this.lastError = null;
			MapSyncMod.getMod().handleSyncDisconnection(SyncClient.this, new CloseContext.Closed(closeCode, reason));
		}

		@Override
		public void onError(
			final @NotNull Exception e
		) {
			LOGGER.warn("[{}] Closing due to error...", SyncClient.this.name(), e);
			SyncClient.this.shouldReconnect = false;
			SyncClient.this.lastError = e.getMessage();
			this.close(CloseContext.CUSTOM_CLOSE_4000_ERROR);
		}

		@Override
		public void onMessage(
			final @NotNull String payload
		) {
			this.onError(new IOException("server sent a text message"));
		}

		@Override
		public void onMessage(
			final @NotNull ByteBuffer payload
		) {
			final int payloadLength = payload.remaining();
			if (payloadLength > MAX_PAYLOAD_LENGTH) {
				this.onError(new IOException("server sent a payload too large! [%d > %d]".formatted(
					payloadLength,
					MAX_PAYLOAD_LENGTH
				)));
				return;
			}
			final Packet packet;
			try {
				packet = Packet.decodePacket(new BufferReader(payload));
			}
			catch (final Exception e) {
				this.onError(e);
				return;
			}
			MapSyncMod.debugLog("[%s] Received %s".formatted(
				SyncClient.this.name(),
				packet
			));
			if (payload.hasRemaining()) {
				this.onError(new IllegalStateException("packet didn't consume all payload bytes! [remaining: %d]".formatted(
					payload.remaining()
				)));
				return;
			}
			try {
				MapSyncMod.getMod().handleSyncPacket(SyncClient.this, packet);
			}
			catch (final Exception e) {
				this.onError(e);
				return;
			}
		}
	}

	public synchronized void send(
		final @NotNull Packet packet
	) {
		Objects.requireNonNull(packet);
		final byte[] packetBytes;
		try {
			final var out = new ByteArrayOutputStream();
			Packet.encodePacket(new BufferWriter(out), packet);
			packetBytes = out.toByteArray();
		}
		catch (final Exception e) {
			this.websocket.onError(e);
			return;
		}
		if (packetBytes.length > MAX_PAYLOAD_LENGTH) {
			this.websocket.onError(new IOException("encoded packet[%s] exceeds maximum payload length! [%d > %d]".formatted(
				packet.getClass().getSimpleName(),
				packetBytes.length,
				MAX_PAYLOAD_LENGTH
			)));
			return;
		}
		try {
			this.websocket.send(packetBytes);
		}
		catch (final WebsocketNotConnectedException e) {
			LOGGER.warn("[{}] Dropping packet[{}] as websocket is not connected!", this.name(), packet.getClass().getSimpleName(), e);
			return;
		}
		catch (final Exception e) {
			this.websocket.onError(e);
			return;
		}
		MapSyncMod.debugLog("[%s] Sent %s".formatted(
			this.name(),
			packet
		));
	}
}
