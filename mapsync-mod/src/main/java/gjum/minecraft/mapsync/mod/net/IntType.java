package gjum.minecraft.mapsync.mod.net;

import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public enum IntType {
	U8 {
		@Override
		public int read(
			final @NotNull ByteBuf source
		) {
			return source.readUnsignedByte();
		}
		@Override
		public void write(
			final @NotNull ByteBuf sink,
			final int value
		) {
			if (value < 0 || value >= 0xFF) {
				throw new IllegalArgumentException("illegal U8 value! [%d]".formatted(
					value
				));
			}
			sink.writeByte(value);
		}
	},
	U16 {
		@Override
		public int read(
			final @NotNull ByteBuf source
		) {
			return source.readUnsignedShort();
		}
		@Override
		public void write(
			final @NotNull ByteBuf sink,
			final int value
		) {
			if (value < 0 || value >= 0xFF_FF) {
				throw new IllegalArgumentException("illegal U16 value! [%d]".formatted(
					value
				));
			}
			sink.writeShort(value);
		}
	},
	;

	public abstract int read(
		@NotNull ByteBuf source
	);

	public abstract void write(
		@NotNull ByteBuf sink,
		int value
	);
}
