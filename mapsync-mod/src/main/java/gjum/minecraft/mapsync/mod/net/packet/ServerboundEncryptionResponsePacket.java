package gjum.minecraft.mapsync.mod.net.packet;

import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.buffers.BufferWriter;
import org.jetbrains.annotations.NotNull;

/**
 * This is sent to the server in response to a {@link ClientboundEncryptionRequestPacket},
 * after which, if the connection persists, you are considered authenticated
 * with the server. You should then receive a {@link ClientboundRegionTimestampsPacket}.
 */
public class ServerboundEncryptionResponsePacket implements Packet {
	public static final int PACKET_ID = 3;

	/**
	 * encrypted with server's public key
	 */
	public final byte[] sharedSecret;
	/**
	 * encrypted with server's public key
	 */
	public final byte[] verifyToken;

	public ServerboundEncryptionResponsePacket(byte[] sharedSecret, byte[] verifyToken) {
		this.sharedSecret = sharedSecret;
		this.verifyToken = verifyToken;
	}

	@Override
	public void write(@NotNull BufferWriter writer) throws Exception {
		writer.writeLengthPrefixedBytes(BufferWriter::writeUnt8, sharedSecret);
		writer.writeLengthPrefixedBytes(BufferWriter::writeUnt8, verifyToken);
	}
}
