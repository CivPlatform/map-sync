package gjum.minecraft.mapsync.common.net.encryption;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

import javax.crypto.Cipher;
import javax.crypto.ShortBufferException;

public class EncryptionTranslator {
	private final Cipher cipher;
	private byte[] inputBuffer = new byte[0];
	private byte[] outputBuffer = new byte[0];

	protected EncryptionTranslator(Cipher cipher) {
		this.cipher = cipher;
	}

	private byte[] bufToBytes(ByteBuf buf) {
		int i = buf.readableBytes();

		if (this.inputBuffer.length < i) {
			this.inputBuffer = new byte[i];
		}

		buf.readBytes(this.inputBuffer, 0, i);
		return this.inputBuffer;
	}

	protected ByteBuf decipher(ChannelHandlerContext ctx, ByteBuf buffer) throws ShortBufferException {
		int i = buffer.readableBytes();
		byte[] bytes = this.bufToBytes(buffer);
		ByteBuf bytebuf = ctx.alloc().heapBuffer(this.cipher.getOutputSize(i));
		bytebuf.writerIndex(this.cipher.update(bytes, 0, i, bytebuf.array(), bytebuf.arrayOffset()));
		return bytebuf;
	}

	protected void encipher(ByteBuf in, ByteBuf out) throws ShortBufferException {
		int i = in.readableBytes();
		byte[] bytes = this.bufToBytes(in);
		int j = this.cipher.getOutputSize(i);

		if (this.outputBuffer.length < j) {
			this.outputBuffer = new byte[j];
		}

		out.writeBytes(this.outputBuffer, 0, this.cipher.update(bytes, 0, i, this.outputBuffer));
	}
}
