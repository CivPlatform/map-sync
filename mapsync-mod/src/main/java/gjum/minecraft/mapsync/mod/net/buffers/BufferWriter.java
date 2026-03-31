package gjum.minecraft.mapsync.mod.net.buffers;

import gjum.minecraft.mapsync.mod.utils.Assertions;
import gjum.minecraft.mapsync.mod.utils.MagicValues;
import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

public final class BufferWriter {
	private final DataOutput internal;

	public BufferWriter(
		final @NotNull ByteArrayOutputStream internal
	) {
		this.internal = new DataOutputStream(Objects.requireNonNull(internal));
	}

	public void writeUnt5(
		final int value
	) throws Exception {
		Assertions.assertIntRange(MagicValues.UNT5_RANGE, value);
		this.internal.writeByte(value);
	}

	public void writeUnt8(
		final int value
	) throws Exception {
		Assertions.assertIntRange(MagicValues.UNT8_RANGE, value);
		this.internal.writeByte(value);
	}

	public void writeUnt10(
		final int value
	) throws Exception {
		Assertions.assertIntRange(MagicValues.UNT10_RANGE, value);
		this.internal.writeShort(value);
	}

	public void writeUnt16(
		final int value
	) throws Exception {
		Assertions.assertIntRange(MagicValues.UNT16_RANGE, value);
		this.internal.writeShort(value);
	}

	public void writeInt16(
		final int value
	) throws Exception {
		Assertions.assertIntRange(MagicValues.INT16_RANGE, value);
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
		final byte[] array
	) throws Exception {
		if (ArrayUtils.getLength(array) > 0) {
			this.internal.write(array);
		}
	}

	public void writeLengthPrefixedBytes(
		final @NotNull LengthPrefixSetter lengthSetter,
		final byte[] array
	) throws Exception {
		lengthSetter.writeLength(this, ArrayUtils.getLength(array));
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
