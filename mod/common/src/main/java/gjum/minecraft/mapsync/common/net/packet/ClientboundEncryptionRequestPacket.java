package gjum.minecraft.mapsync.common.net.packet;

import gjum.minecraft.mapsync.common.net.Packet;
import io.netty.buffer.ByteBuf;

import javax.annotation.Nonnull;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

/**
 * You will receive this in response to {@link ServerboundHandshakePacket}, and
 * will expect a {@link ServerboundEncryptionResponsePacket} in response.
 */
public class ClientboundEncryptionRequestPacket implements Packet {
	public static final int PACKET_ID = 2;

	@Nonnull
	public final PublicKey publicKey;
	@Nonnull
	public final byte[] verifyToken;

	public ClientboundEncryptionRequestPacket(@Nonnull PublicKey publicKey, @Nonnull byte[] verifyToken) {
		this.publicKey = publicKey;
		this.verifyToken = verifyToken;
	}

	public static Packet read(ByteBuf buf) {
		return new ClientboundEncryptionRequestPacket(
				readKey(buf),
				Packet.readIntLengthByteArray(buf));
	}

	protected static PublicKey readKey(ByteBuf in) {
		try {
			byte[] encodedKey = Packet.readIntLengthByteArray(in);
			X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encodedKey);
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			return keyFactory.generatePublic(keySpec);
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			throw new RuntimeException(e);
		}
	}
}
