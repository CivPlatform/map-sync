package gjum.minecraft.mapsync.mod.net.packet;

import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.buffers.BufferReader;
import gjum.minecraft.mapsync.mod.utils.Assertions;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import org.jetbrains.annotations.NotNull;

/// - Prev: [ServerboundHandshakePacket]
/// - Next: [ServerboundEncryptionResponsePacket]
public record ClientboundEncryptionRequestPacket(
	@NotNull PublicKey publicKey,
	byte @NotNull [] verifyToken
) implements Packet {
	public static final int PACKET_ID = 2;

	public ClientboundEncryptionRequestPacket {
		Assertions.assertNotNull(publicKey);
		Assertions.assertNotNull(verifyToken);
	}

	public static @NotNull Packet read(
		final @NotNull BufferReader reader
	) throws Exception {
		return new ClientboundEncryptionRequestPacket(
			KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(
				reader.readBytesOfLength(reader.readUnt16())
			)),
			reader.readBytesOfLength(reader.readUnt8())
		);
	}
}
