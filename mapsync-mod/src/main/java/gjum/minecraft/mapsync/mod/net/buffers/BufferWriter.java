package gjum.minecraft.mapsync.mod.net.buffers;

import gjum.minecraft.mapsync.mod.utils.Assertions;
import gjum.minecraft.mapsync.mod.utils.MagicValues;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public final class BufferWriter {
	private final ByteBuf internal;

	public BufferWriter(
		final @NotNull ByteBuf internal
	) {
		this.internal = Objects.requireNonNull(internal);
	}

	public void writeUnt5(
		final int value
	) throws Exception {
		this.internal.writeByte((int) Assertions.assertMasked(MagicValues.UNT5_MASK, value));
	}

	public void writeUnt8(
		final int value
	) throws Exception {
		this.internal.writeByte((int) Assertions.assertMasked(MagicValues.UNT8_MASK, value));
	}

	public void writeUnt10(
		final int value
	) throws Exception {
		this.internal.writeShort((int) Assertions.assertMasked(MagicValues.UNT10_MASK, value));
	}

	public void writeUnt16(
		final int value
	) throws Exception {
		this.internal.writeShort((int) Assertions.assertMasked(MagicValues.UNT16_MASK, value));
	}

	public void writeInt16(
		final short value
	) throws Exception {
		this.internal.writeShort(value);
	}

	public void writeInt32(
		final int value
	) throws Exception {
		this.internal.writeInt(value);
	}

	public void writeInt64(
		final long value
	) throws Exception {
		this.internal.writeLong(value);
	}

	public void writeBytes(
		final byte @NotNull [] array
	) throws Exception {
		if (array.length > 0) {
			this.internal.writeBytes(array);
		}
	}

	public void writeLengthPrefixedBytes(
		final @NotNull LengthPrefixSetter lengthSetter,
		final byte @NotNull [] array
	) throws Exception {
		lengthSetter.writeLength(this, array.length);
		this.writeBytes(array);
	}

	/// Converts a string to UTF-8 bytes and writes it with a u8 length-prefix.
	public void writeString(
		final @NotNull String string
	) throws Exception {
		this.writeLengthPrefixedBytes(
			BufferWriter::writeUnt8,
			string.getBytes(StandardCharsets.UTF_8)
		);
	}

	@FunctionalInterface
	public interface LengthPrefixSetter {
		void writeLength(
			@NotNull BufferWriter writer,
			int length
		) throws Exception;
	}
}
