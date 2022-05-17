package gjum.minecraft.mapsync.common.net.packet;

import gjum.minecraft.mapsync.common.net.Packet;
import io.netty.buffer.ByteBuf;

public class CEncryptionResponse extends Packet {
	public static final int PACKET_ID = 3;

	/**
	 * encrypted with server's public key
	 */
	public final byte[] sharedSecret;
	/**
	 * encrypted with server's public key
	 */
	public final byte[] verifyToken;

	public CEncryptionResponse(byte[] sharedSecret, byte[] verifyToken) {
		this.sharedSecret = sharedSecret;
		this.verifyToken = verifyToken;
	}

	public static Packet read(ByteBuf buf) {
		return new CEncryptionResponse(readByteArray(buf), readByteArray(buf));
	}

	@Override
	public void write(ByteBuf out) {
		writeByteArray(out, sharedSecret);
		writeByteArray(out, verifyToken);
	}
}
