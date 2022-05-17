package gjum.minecraft.mapsync.common.net.packet;

import gjum.minecraft.mapsync.common.net.Packet;
import io.netty.buffer.ByteBuf;

import javax.annotation.Nonnull;
import java.security.PublicKey;

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
}
