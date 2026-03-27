package gjum.minecraft.mapsync.mod.net.packet;

import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.buffers.BufferWriter;
import gjum.minecraft.mapsync.mod.utils.Assertions;
import org.jetbrains.annotations.NotNull;

/// This is sent to the server in response to a [ClientboundEncryptionRequestPacket], after which, if the connection
/// persists, you are considered authenticated with the server. You should then receive a [ClientboundRegionTimestampsPacket].
///
/// - Prev: [ClientboundEncryptionRequestPacket]
/// - Next: [ClientboundRegionTimestampsPacket]
///
/// @param sharedSecret encrypted with server's public key
/// @param verifyToken encrypted with server's public key
public record ServerboundEncryptionResponsePacket(
	byte @NotNull [] sharedSecret,
	byte @NotNull [] verifyToken
) implements Packet {
	public static final int PACKET_ID = 3;

	public ServerboundEncryptionResponsePacket {
		Assertions.assertNotNull(sharedSecret);
		Assertions.assertNotNull(verifyToken);
	}

	@Override
	public void write(
		final @NotNull BufferWriter writer
	) throws Exception {
		writer.writeLengthPrefixedBytes(BufferWriter::writeUnt8, this.sharedSecret());
		writer.writeLengthPrefixedBytes(BufferWriter::writeUnt8, this.verifyToken());
	}
}
