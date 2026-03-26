package gjum.minecraft.mapsync.mod.net.buffers;

import gjum.minecraft.mapsync.mod.utils.Assertions;
import gjum.minecraft.mapsync.mod.utils.MagicValues;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.NotNull;

public final class BufferReader {
	private final ByteBuf internal;

	public BufferReader(
		final @NotNull ByteBuf internal
	) {
		this.internal = Objects.requireNonNull(internal);
	}

	public int readUnt5() throws Exception {
		return (int) Assertions.assertMasked(MagicValues.UNT5_MASK, this.readUnt8());
	}

	public int readUnt8() throws Exception {
		return this.internal.readUnsignedByte();
	}

	public int readUnt10() throws Exception {
		return (int) Assertions.assertMasked(MagicValues.UNT10_MASK, this.readUnt16());
	}

	public int readUnt16() throws Exception {
		return this.internal.readUnsignedShort();
	}

	public int readInt16() throws Exception {
		return this.internal.readShort();
	}

	public int readInt32() throws Exception {
		return this.internal.readInt();
	}

	public long readInt64() throws Exception {
		return this.internal.readLong();
	}

	public byte @NotNull [] readBytesOfLength(
		final int size
	) throws Exception {
		final var bytes = new byte[size];
		if (size > 0) {
			this.internal.readBytes(bytes);
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
