package gjum.minecraft.mapsync.common.net.packet;

import gjum.minecraft.mapsync.common.net.Packet;
import io.netty.buffer.ByteBuf;

import javax.annotation.Nonnull;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

public class SEncryptionRequest extends Packet {
	public static final int PACKET_ID = 2;

	@Nonnull
	public final PublicKey publicKey;
	@Nonnull
	public final byte[] verifyToken;

	public SEncryptionRequest(@Nonnull PublicKey publicKey, @Nonnull byte[] verifyToken) {
		this.publicKey = publicKey;
		this.verifyToken = verifyToken;
	}

	public static Packet read(ByteBuf buf) {
		return new SEncryptionRequest(
				readKey(buf),
				readByteArray(buf));
	}

	@Override
	public void write(ByteBuf buf) {
		writeByteArray(buf, publicKey.getEncoded());
		writeByteArray(buf, verifyToken);
	}

	protected static PublicKey readKey(ByteBuf in) {
		try {
			byte[] encodedKey = readByteArray(in);
			X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encodedKey);
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			return keyFactory.generatePublic(keySpec);
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			throw new RuntimeException(e);
		}
	}
}
