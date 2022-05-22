package gjum.minecraft.mapsync.common.net;

import io.netty.buffer.ByteBuf;

public abstract class Packet {
	public abstract void write(ByteBuf buf);

	protected static byte[] readByteArray(ByteBuf in) {
		int length = in.readInt();
		byte[] array = new byte[length];
		in.readBytes(array);
		return array;
	}

	protected static void writeByteArray(ByteBuf out, byte[] array) {
		out.writeInt(array.length);
		out.writeBytes(array);
	}
}
