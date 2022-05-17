package gjum.minecraft.mapsync.common.net.encryption;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import javax.crypto.Cipher;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import java.security.GeneralSecurityException;
import java.security.Key;

public class EncryptionEncoder extends MessageToByteEncoder<ByteBuf> {
	private final EncryptionTranslator encryptionCodec;

	public EncryptionEncoder(Key key) {
		try {
			Cipher cipher = Cipher.getInstance("AES/CFB8/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(key.getEncoded()));
			encryptionCodec = new EncryptionTranslator(cipher);
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws ShortBufferException {
		encryptionCodec.encipher(in, out);
	}
}
