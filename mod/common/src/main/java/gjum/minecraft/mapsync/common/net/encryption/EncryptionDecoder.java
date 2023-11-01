package gjum.minecraft.mapsync.common.net.encryption;

import gjum.minecraft.mapsync.common.net.Packet;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

public class EncryptionDecoder extends MessageToMessageDecoder<ByteBuf> {
	private final Cipher cipher;

	public EncryptionDecoder(
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
	protected void decode(
			final ChannelHandlerContext ctx,
			final ByteBuf in,
			final List<Object> out
	) throws ShortBufferException {
		final byte[] receivedBytes = Packet.readByteArrayOfSize(in, in.readableBytes());
		final byte[] decryptedBytes = this.cipher.update(receivedBytes);
		if (ArrayUtils.getLength(decryptedBytes) > 0) {
			out.add(Unpooled.wrappedBuffer(decryptedBytes));
		}
	}
}
