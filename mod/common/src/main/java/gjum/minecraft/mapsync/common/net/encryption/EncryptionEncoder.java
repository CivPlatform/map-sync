package gjum.minecraft.mapsync.common.net.encryption;

import gjum.minecraft.mapsync.common.net.Packet;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import java.security.GeneralSecurityException;
import java.security.Key;
import javax.crypto.Cipher;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

public class EncryptionEncoder extends MessageToByteEncoder<ByteBuf> {
	private final Cipher cipher;

	public EncryptionEncoder(
			final @NotNull Key key
	) {
		try {
			this.cipher = Cipher.getInstance("AES/CFB8/NoPadding");
			this.cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(key.getEncoded()));
		}
		catch (final GeneralSecurityException thrown) {
			throw new IllegalStateException(thrown);
		}
    }

	@Override
	protected void encode(
			final ChannelHandlerContext ctx,
			final ByteBuf in,
			final ByteBuf out
	) throws ShortBufferException {
		final byte[] sendingBytes = Packet.readByteArrayOfSize(in, in.readableBytes());
		final byte[] encryptedBytes = this.cipher.update(sendingBytes);
		if (ArrayUtils.getLength(encryptedBytes) > 0) {
			out.writeBytes(encryptedBytes);
		}
	}
}
