package gjum.minecraft.mapsync.mod.server.net.auth;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/// Server-side mirror of the client's auth state machine. The handshake
/// runs: connect → AwaitingHandshake → (HandshakePacket) →
/// AwaitingIdentityResponse(serverSalt) → (IdentityResponsePacket) →
/// Welcomed(name, uuid, authed). Each ProtocolHandler entry point
/// pattern-matches on the current state to reject out-of-order packets.
public sealed interface ServerAuthState
	permits ServerAuthState.AwaitingHandshake,
	        ServerAuthState.AwaitingIdentityResponse,
	        ServerAuthState.Welcomed {

	/// Display suffix used in log lines so an unauthed connection still
	/// shows something useful in operator-facing output.
	@NotNull String logName();

	record AwaitingHandshake() implements ServerAuthState {
		@Override
		public @NotNull String logName() {
			return "pre-handshake";
		}
	}

	record AwaitingIdentityResponse(byte @NotNull [] serverSalt) implements ServerAuthState {
		@Override
		public @NotNull String logName() {
			return "pre-identity";
		}
	}

	record Welcomed(
		@NotNull String name,
		@NotNull UUID uuid,
		boolean authed
	) implements ServerAuthState {
		@Override
		public @NotNull String logName() {
			return this.name + (this.authed ? "" : "?");
		}
	}
}
