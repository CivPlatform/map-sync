package gjum.minecraft.mapsync.mod.server.net;

import gjum.minecraft.mapsync.mod.deps.websockets.WebSocket;
import gjum.minecraft.mapsync.mod.deps.websockets.exceptions.WebsocketNotConnectedException;
import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.buffers.BufferWriter;
import gjum.minecraft.mapsync.mod.server.net.auth.ServerAuthState;
import gjum.minecraft.mapsync.mod.utils.MagicValues;
import java.io.ByteArrayOutputStream;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Server-side mirror of the client mod's SyncClient: one instance per
/// accepted websocket. Stores the per-connection auth state, the player's
/// claimed game address, and the dimension they last said they were in.
/// Stays small on purpose — the protocol logic lives in
/// {@link ProtocolHandler}; this class just bridges the websocket bytes
/// to the Packet API and offers a typed `send` for clientbound replies.
///
/// Mutable state is volatile and read from arbitrary threads (Java-WebSocket
/// dispatches reads on worker threads). The protocol handler serializes
/// transitions per-connection so we never need locks for ordering, only
/// for visibility.
public final class WsServerClient {
	private static final Logger logger = LoggerFactory.getLogger(WsServerClient.class);

	private final @NotNull WebSocket conn;
	public final long id;

	public volatile @NotNull ServerAuthState auth = new ServerAuthState.AwaitingHandshake();
	public volatile @Nullable String gameAddress = null;
	public volatile @Nullable Identifier dimension = null;

	WsServerClient(
		final @NotNull WebSocket conn,
		final long id
	) {
		this.conn = conn;
		this.id = id;
	}

	public @NotNull String name() {
		return "Client" + this.id + ":" + this.auth.logName();
	}

	public boolean isInDimension(
		final @NotNull Identifier dim
	) {
		return dim.equals(this.dimension);
	}

	public ServerAuthState.@NotNull Welcomed requireWelcomed() {
		if (this.auth instanceof final ServerAuthState.Welcomed welcomed) {
			return welcomed;
		}
		throw new IllegalStateException("client not welcomed: " + this.auth);
	}

	public boolean isWelcomed() {
		return this.auth instanceof ServerAuthState.Welcomed;
	}

	/// Encodes the given packet to its wire form and writes it to the
	/// underlying websocket. Drops the send silently when the connection is
	/// no longer open (close races); kicks the client on encode failure
	/// since a malformed clientbound packet would corrupt the stream.
	public void send(
		final @NotNull Packet packet
	) {
		if (!this.conn.isOpen()) {
			return;
		}
		final byte[] bytes;
		try {
			final var out = new ByteArrayOutputStream();
			Packet.encodePacket(new BufferWriter(out), packet);
			bytes = out.toByteArray();
		}
		catch (final Exception e) {
			logger.warn("[{}] Failed to encode {}; kicking", this.name(), packet.getClass().getSimpleName(), e);
			this.kick("encode failure: " + e.getMessage());
			return;
		}
		this.sendRaw(bytes);
	}

	/// Writes pre-encoded wire bytes (already including the packet-id byte).
	/// Used by the catchup path, which constructs ChunkTilePackets directly
	/// from stored bytes without parsing them through BlockColumn.
	public void sendRaw(
		final byte @NotNull [] wireBytes
	) {
		if (!this.conn.isOpen()) {
			return;
		}
		try {
			this.conn.send(wireBytes);
		}
		catch (final WebsocketNotConnectedException ignored) {
			// Close race; nothing to do.
		}
		catch (final Exception e) {
			logger.warn("[{}] send threw", this.name(), e);
			this.kick("send failure: " + e.getMessage());
		}
	}

	/// Closes the connection with the standard MapSync kick code. The
	/// `internalReason` is logged but never forwarded over the wire — the
	/// protocol carries no human-readable kick reason.
	public void kick(
		final @NotNull String internalReason
	) {
		logger.info("[{}] kicking: {}", this.name(), internalReason);
		this.conn.close(MagicValues.CLOSE_CODE_KICKED);
	}
}
