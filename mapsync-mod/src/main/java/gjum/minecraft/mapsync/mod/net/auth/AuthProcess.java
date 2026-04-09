package gjum.minecraft.mapsync.mod.net.auth;

import gjum.minecraft.mapsync.mod.sync.DimensionState;
import gjum.minecraft.mapsync.mod.net.SyncClient;
import gjum.minecraft.mapsync.mod.net.UnexpectedPacketException;
import gjum.minecraft.mapsync.mod.net.packet.ClientboundIdentityRequestPacket;
import gjum.minecraft.mapsync.mod.net.packet.ClientboundWelcomePacket;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundHandshakePacket;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundIdentityResponsePacket;
import gjum.minecraft.mapsync.mod.utils.MagicValues;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.jetbrains.annotations.NotNull;

public final class AuthProcess {
	private record AwaitingIdentityRequest() implements AuthState {}
	public static void sendHandshake(
		final @NotNull SyncClient client
	) throws Exception {
		final DimensionState dimensionState = client.gameContext.getDimensionState().orElse(null);
		if (dimensionState == null) {
			throw new IllegalStateException("no dimension state");
		}
		if (!client.authState.setIf(Objects::isNull, AwaitingIdentityRequest::new)) {
			throw new IllegalStateException("already authenticated");
		}
		client.send(new ServerboundHandshakePacket(
			MagicValues.VERSION,
			client.gameContext.getGameAddress(),
			dimensionState.dimension.identifier().toString()
		));
	}

	private record AwaitingWelcome() implements AuthState {}
	public static void handleIdentityRequest(
		final @NotNull SyncClient client,
		final @NotNull ClientboundIdentityRequestPacket packet
	) throws Exception {
		if (!client.authState.setIf((state) -> state instanceof AwaitingIdentityRequest, AwaitingWelcome::new)) {
			throw new UnexpectedPacketException(packet);
		}
		final User session = Minecraft.getInstance().getUser();
		final byte[] serverSalt = packet.serverSalt();
		final byte[] clientSalt = new byte[serverSalt.length];
		final boolean requiresAuth = clientSalt.length > 0;
		if (requiresAuth) {
			ThreadLocalRandom.current().nextBytes(clientSalt);
			final String serverIdHex; {
				final MessageDigest md = MessageDigest.getInstance("SHA-1");
				md.update(serverSalt);
				md.update(clientSalt);
				serverIdHex = HexFormat.of().formatHex(md.digest());
			}
			Minecraft.getInstance().services().sessionService().joinServer(
				session.getProfileId(),
				session.getAccessToken(),
				serverIdHex
			);
		}
		client.send(new ServerboundIdentityResponsePacket(
			session.getName(),
			clientSalt
		));
	}

	public static void handleWelcome(
		final @NotNull SyncClient client,
		final @NotNull ClientboundWelcomePacket packet
	) throws Exception {
		if (!client.authState.setIf((state) -> state instanceof AwaitingWelcome, Welcomed::new)) {
			throw new UnexpectedPacketException(packet);
		}
	}
}
