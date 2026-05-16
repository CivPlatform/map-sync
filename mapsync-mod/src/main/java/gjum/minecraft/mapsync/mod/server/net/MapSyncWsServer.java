package gjum.minecraft.mapsync.mod.server.net;

import gjum.minecraft.mapsync.mod.deps.websockets.WebSocket;
import gjum.minecraft.mapsync.mod.deps.websockets.handshake.ClientHandshake;
import gjum.minecraft.mapsync.mod.deps.websockets.server.WebSocketServer;
import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.buffers.BufferReader;
import gjum.minecraft.mapsync.mod.net.packet.ChunkTilePacket;
import gjum.minecraft.mapsync.mod.server.MapSyncServerState;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Java port of `mapsync-server/src/server.ts` (WSServer). Accepts websocket
/// connections, attaches a per-connection {@link WsServerClient} to each,
/// and hands decoded {@link Packet}s to a {@link ProtocolHandler}. Lifecycle
/// is owned by {@link MapSyncServerState}: started after SERVER_STARTED so
/// the world and player list are ready, stopped on SERVER_STOPPING so the
/// listening socket releases the port before the JVM hands off.
///
/// Threading: the underlying Java-WebSocket server runs one selector thread
/// plus a pool of worker threads (one per CPU). All four event hooks
/// (`onOpen`, `onClose`, `onMessage`, `onError`) fire on worker threads;
/// long-running work (notably Mojang auth, which blocks on HTTP) is offloaded
/// by the ProtocolHandler so we don't starve the workers.
public final class MapSyncWsServer extends WebSocketServer {
	private static final Logger logger = LoggerFactory.getLogger(MapSyncWsServer.class);

	private final @NotNull MapSyncServerState state;
	private final @NotNull ProtocolHandler handler;
	private final @NotNull AtomicLong nextClientId = new AtomicLong(1);

	public MapSyncWsServer(
		final @NotNull MapSyncServerState state,
		final @NotNull ProtocolHandler handler
	) {
		super(new InetSocketAddress(state.config().host, state.config().port));
		this.state = state;
		this.handler = handler;
		setReuseAddr(true);
		setConnectionLostTimeout(30);
	}

	public @NotNull MapSyncServerState state() {
		return this.state;
	}

	/// Snapshot of the currently-attached clients. Defensive copy so callers
	/// (operator commands, broadcast loops) can iterate without holding the
	/// websocket library's internal connection lock.
	public @NotNull List<WsServerClient> activeClients() {
		final List<WsServerClient> out = new ArrayList<>();
		for (final WebSocket conn : getConnections()) {
			final WsServerClient client = conn.getAttachment();
			if (client != null) out.add(client);
		}
		return out;
	}

	public @NotNull Collection<WebSocket> connections() {
		return getConnections();
	}

	@Override
	public void onStart() {
		logger.info("MapSync websocket server listening on {}", getAddress());
	}

	@Override
	public void onOpen(
		final @NotNull WebSocket conn,
		final @NotNull ClientHandshake handshake
	) {
		final WsServerClient client = new WsServerClient(conn, nextClientId.getAndIncrement());
		conn.setAttachment(client);
		logger.info("[{}] connected from {}", client.name(), conn.getRemoteSocketAddress());
		try {
			this.handler.handleClientConnected(client);
		}
		catch (final Exception e) {
			logger.warn("[{}] connect-handler threw; kicking", client.name(), e);
			client.kick("connect-handler threw: " + e.getMessage());
		}
	}

	@Override
	public void onClose(
		final @NotNull WebSocket conn,
		final int code,
		final @NotNull String reason,
		final boolean remote
	) {
		final WsServerClient client = conn.getAttachment();
		if (client == null) return;
		logger.info("[{}] closed (code={}, remote={})", client.name(), code, remote);
		try {
			this.handler.handleClientDisconnected(client);
		}
		catch (final Exception e) {
			logger.warn("[{}] disconnect-handler threw", client.name(), e);
		}
	}

	/// Text frames have no place in our protocol; reject loudly so client
	/// authors notice during development instead of debugging a silent
	/// no-op.
	@Override
	public void onMessage(
		final @NotNull WebSocket conn,
		final @NotNull String message
	) {
		final WsServerClient client = conn.getAttachment();
		if (client != null) {
			client.kick("text frame on binary-only protocol");
		}
		else {
			conn.close(1003);
		}
	}

	@Override
	public void onMessage(
		final @NotNull WebSocket conn,
		final @NotNull ByteBuffer message
	) {
		final WsServerClient client = conn.getAttachment();
		if (client == null) {
			conn.close(1011); // attachment lost
			return;
		}
		// Capture the full frame before any read advances `message.position`.
		// ChunkTilePackets are relayed verbatim to other clients without
		// re-encoding, so we hand these original bytes to the protocol
		// handler.
		final byte[] wireBytes = new byte[message.remaining()];
		message.duplicate().get(wireBytes);

		final BufferReader reader = new BufferReader(message);
		final int packetId;
		try {
			packetId = reader.readUnt8();
		}
		catch (final Exception e) {
			client.kick("could not read packet id: " + e.getMessage());
			return;
		}
		try {
			if (packetId == ChunkTilePacket.PACKET_ID) {
				final ServerChunkTileFrame frame = ServerChunkTileFrame.decodeAfterPacketId(reader, message, wireBytes);
				this.handler.handleChunkTileFrame(client, frame);
			}
			else {
				final Packet packet = Packet.decodeServerboundFromId(packetId, reader);
				if (message.hasRemaining()) {
					client.kick("trailing bytes after packet (" + message.remaining() + " left)");
					return;
				}
				this.handler.handleClientPacket(client, packet);
			}
		}
		catch (final Exception e) {
			logger.warn("[{}] decode/handler failed for packet id {}; kicking", client.name(), packetId, e);
			client.kick("decode/handler failed: " + e.getMessage());
		}
	}

	@Override
	public void onError(
		final @Nullable WebSocket conn,
		final @NotNull Exception ex
	) {
		if (conn != null) {
			final WsServerClient client = conn.getAttachment();
			if (client != null) {
				logger.warn("[{}] websocket error", client.name(), ex);
				client.kick("websocket error: " + ex.getMessage());
				return;
			}
		}
		// Selector-level error before any client was attached.
		logger.warn("MapSync websocket server error", ex);
	}
}
