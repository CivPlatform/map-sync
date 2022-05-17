package gjum.minecraft.mapsync.common.net.encryption;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import javax.crypto.Cipher;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.util.List;

public class EncryptionDecoder extends MessageToMessageDecoder<ByteBuf> {
	private final EncryptionTranslator decryptionCodec;

	public EncryptionDecoder(Key key) {
		try {
			Cipher cipher = Cipher.getInstance("AES/CFB8/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(key.getEncoded()));
			decryptionCodec = new EncryptionTranslator(cipher);
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws ShortBufferException {
		out.add(decryptionCodec.decipher(ctx, in));
	}
}
