package gjum.minecraft.mapsync.mod.net.packet;

import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.buffers.BufferReader;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import org.jetbrains.annotations.NotNull;

/**
 * You will receive this in response to {@link ServerboundHandshakePacket}, and
 * will expect a {@link ServerboundEncryptionResponsePacket} in response.
 */
public class ClientboundEncryptionRequestPacket implements Packet {
	public static final int PACKET_ID = 2;

	public final @NotNull PublicKey publicKey;
	public final byte @NotNull [] verifyToken;

	public ClientboundEncryptionRequestPacket(@NotNull PublicKey publicKey, byte @NotNull [] verifyToken) {
		this.publicKey = publicKey;
		this.verifyToken = verifyToken;
	}

	public static Packet read(BufferReader reader) throws Exception {
		return new ClientboundEncryptionRequestPacket(
				readKey(reader),
				reader.readBytesOfLength(reader.readUnt8()));
	}

	protected static PublicKey readKey(BufferReader reader) throws Exception {
		try {
			byte[] encodedKey = reader.readBytesOfLength(reader.readUnt16());
			X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encodedKey);
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			return keyFactory.generatePublic(keySpec);
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			throw new RuntimeException(e);
		}
	}
}
