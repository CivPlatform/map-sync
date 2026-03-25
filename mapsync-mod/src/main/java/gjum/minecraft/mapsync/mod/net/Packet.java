package gjum.minecraft.mapsync.mod.net;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;

public interface Packet {
	public default void write(@NotNull ByteBuf out) {
		throw new NotImplementedException();
	}

	public static byte @NotNull [] readBytesOfLength(
		final @NotNull ByteBuf in,
		final int size
	) {
		final var bytes = new byte[size];
		if (size > 0) {
			in.readBytes(bytes);
		}
		return bytes;
	}

	public static byte @NotNull [] readLengthPrefixedBytes(
		final @NotNull ByteBuf in,
		final @NotNull IntType lengthPrefix
	) {
		return readBytesOfLength(in, lengthPrefix.read(in));
	}

	public static void writeLengthPrefixedBytes(
		final @NotNull ByteBuf out,
		final @NotNull IntType lengthPrefix,
		final byte @NotNull [] array
	) {
		if (array.length > 0) {
			lengthPrefix.write(out, array.length);
			out.writeBytes(array);
		}
		else {
			lengthPrefix.write(out, 0);
		}
	}

	public static @NotNull String readLengthPrefixedString(
		final @NotNull ByteBuf in,
		final @NotNull IntType lengthPrefix
	) {
		return new String(
			readLengthPrefixedBytes(in, lengthPrefix),
			StandardCharsets.UTF_8
		);
	}

	public static void writeLengthPrefixedString(
		final @NotNull ByteBuf out,
		final @NotNull IntType lengthPrefix,
		final @NotNull String string
	) {
		writeLengthPrefixedBytes(
			out,
			lengthPrefix,
			string.getBytes(StandardCharsets.UTF_8)
		);
	}

	public static <T, R extends ResourceKey<Registry<T>>> @NotNull ResourceKey<T> readResourceKey(
		final @NotNull ByteBuf in,
		final @NotNull IntType lengthPrefix,
		final @NotNull R registry
	) {
		return ResourceKey.create(
			registry,
			Identifier.parse(readLengthPrefixedString(in, lengthPrefix))
		);
	}

	public static void writeResourceKey(
		final @NotNull ByteBuf out,
		final @NotNull IntType lengthPrefix,
		final @NotNull ResourceKey<?> resourceKey
	) {
		writeLengthPrefixedString(
			out,
			lengthPrefix,
			resourceKey.identifier().toString()
		);
	}
}
