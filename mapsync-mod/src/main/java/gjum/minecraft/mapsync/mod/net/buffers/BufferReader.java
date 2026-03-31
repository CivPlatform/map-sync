package gjum.minecraft.mapsync.mod.net.buffers;

import gjum.minecraft.mapsync.mod.utils.Assertions;
import gjum.minecraft.mapsync.mod.utils.MagicValues;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.NotNull;

public final class BufferReader {
	private final ByteBuffer internal;

	public BufferReader(
		final @NotNull ByteBuffer internal
	) {
		this.internal = Objects.requireNonNull(internal);
	}

	public boolean readBoolean() throws Exception {
		return Assertions.assertMasked(MagicValues.UNT1_MASK, this.readUnt8()) == 1L;
	}

	public int readUnt5() throws Exception {
		return (int) Assertions.assertMasked(MagicValues.UNT5_MASK, this.readUnt8());
	}

	public int readUnt8() throws Exception {
		return Byte.toUnsignedInt(this.internal.get());
	}

	public int readUnt10() throws Exception {
		return (int) Assertions.assertMasked(MagicValues.UNT10_MASK, this.readUnt16());
	}

	public int readUnt16() throws Exception {
		return Short.toUnsignedInt(this.internal.getShort());
	}

	public int readInt16() throws Exception {
		return this.internal.getShort();
	}

	public int readInt32() throws Exception {
		return this.internal.getInt();
	}

	public long readInt64() throws Exception {
		return this.internal.getLong();
	}

	public byte @NotNull [] readBytesOfLength(
		final int length
	) throws Exception {
		final var bytes = new byte[length];
		if (length > 0) {
			this.internal.get(bytes);
		}
		return bytes;
	}

	/// Reads a u8 length-prefixed UTF-8 string.
	public @NotNull String readString() throws Exception {
		return new String(
			this.readBytesOfLength(this.readUnt8()),
			StandardCharsets.UTF_8
		);
	}

	/// Convenience function
	public @NotNull Identifier readIdentifier() throws Exception {
		return Identifier.parse(this.readString());
	}

	/// Convenience function
	public <T, R extends ResourceKey<Registry<T>>> @NotNull ResourceKey<T> readResourceKey(
		final @NotNull R registry
	) throws Exception {
		return ResourceKey.create(registry, this.readIdentifier());
	}
}
