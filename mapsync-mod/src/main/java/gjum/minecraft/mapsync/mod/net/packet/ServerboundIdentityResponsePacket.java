package gjum.minecraft.mapsync.mod.net.packet;

import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.buffers.BufferReader;
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

	public static @NotNull Packet read(
		final @NotNull BufferReader reader
	) throws Exception {
		return new ServerboundIdentityResponsePacket(
			reader.readString(),
			reader.readBytesOfLength(reader.readUnt8())
		);
	}

	@Override
	public void write(
		final @NotNull BufferWriter writer
	) throws Exception {
		writer.writeString(this.claimedUsername());
		writer.writeLengthPrefixedBytes(BufferWriter::writeUnt8, this.clientSalt());
	}
}
