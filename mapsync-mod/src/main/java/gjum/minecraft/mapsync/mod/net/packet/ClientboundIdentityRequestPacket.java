package gjum.minecraft.mapsync.mod.net.packet;

import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.buffers.BufferReader;
import gjum.minecraft.mapsync.mod.utils.Assertions;
import org.jetbrains.annotations.NotNull;

/// This is sent by the server in response to a [ServerboundHandshakePacket]. The salt is the server's portion of the
/// sha-hex that'll be used during authentication. If the salt is empty, the server does not require authentication.
///
/// - Prev: [ServerboundHandshakePacket]
/// - Next: [ServerboundIdentityResponsePacket]
public record ClientboundIdentityRequestPacket(
	byte @NotNull [] serverSalt
) implements Packet {
	public static final int PACKET_ID = 2;

	public ClientboundIdentityRequestPacket {
		Assertions.assertNotNull(serverSalt);
	}

	public static @NotNull Packet read(
		final @NotNull BufferReader reader
	) throws Exception {
		return new ClientboundIdentityRequestPacket(
			reader.readBytesOfLength(reader.readUnt8())
		);
	}
}
