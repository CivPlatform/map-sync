package gjum.minecraft.mapsync.common.net.packet;

import gjum.minecraft.mapsync.common.net.Packet;
import io.netty.buffer.ByteBuf;

/**
 * This is sent to the server in response to a {@link ClientboundEncryptionRequestPacket},
 * after which, if the connection persists, you are considered authenticated
 * with the server. You should then receive a {@link ClientboundRegionTimestampsPacket}.
 */
public class ServerboundEncryptionResponsePacket extends Packet {
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

	public static Packet read(ByteBuf buf) {
		return new ServerboundEncryptionResponsePacket(readByteArray(buf), readByteArray(buf));
	}

	@Override
	public void write(ByteBuf out) {
		writeByteArray(out, sharedSecret);
		writeByteArray(out, verifyToken);
	}
}
