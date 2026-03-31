package gjum.minecraft.mapsync.mod.net.packet;

import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.buffers.BufferWriter;
import gjum.minecraft.mapsync.mod.utils.Assertions;
import org.jetbrains.annotations.NotNull;

/// This is sent to the server in response to a [ClientboundIdentityRequestPacket]. The salt is the client's portion of
/// the sha-hex that'll be used during authentication. The salt MUST be empty if the server's salt was empty.
///
/// - Prev: [ClientboundIdentityRequestPacket]
/// - Next: [ClientboundWelcomePacket]
public record ServerboundIdentityResponsePacket(
	@NotNull String claimedUsername,
	byte @NotNull [] clientSalt
) implements Packet {
	public static final int PACKET_ID = 3;

	public ServerboundIdentityResponsePacket {
		Assertions.assertNotNull(claimedUsername);
		Assertions.assertNotNull(clientSalt);
	}

	@Override
	public void write(
		final @NotNull BufferWriter writer
	) throws Exception {
		writer.writeString(this.claimedUsername());
		writer.writeLengthPrefixedBytes(BufferWriter::writeUnt8, this.clientSalt());
	}
}
