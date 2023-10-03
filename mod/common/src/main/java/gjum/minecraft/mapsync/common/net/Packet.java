package gjum.minecraft.mapsync.common.net;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;

public interface Packet {
	default void write(@NotNull ByteBuf out) {
		throw new NotImplementedException();
	}

	static byte @NotNull [] readByteArrayOfSize(
			final @NotNull ByteBuf in,
			final int size
	) {
		final var bytes = new byte[size];
		if (size > 0) {
			in.readBytes(bytes);
		}
		return bytes;
	}

	static byte @NotNull [] readIntLengthByteArray(
			final @NotNull ByteBuf in
	) {
		return readByteArrayOfSize(in, in.readInt());
	}

	static void writeIntLengthByteArray(
			final @NotNull ByteBuf out,
			final byte @NotNull [] array
	) {
		if (array.length > 0) {
			out.writeInt(array.length);
			out.writeBytes(array);
		}
		else {
			out.writeInt(0);
		}
	}

	static @NotNull String readUtf8String(
			final @NotNull ByteBuf in
	) {
		return new String(
				readIntLengthByteArray(in),
				StandardCharsets.UTF_8
		);
	}

	static void writeUtf8String(
			final @NotNull ByteBuf out,
			final @NotNull String string
	) {
		writeIntLengthByteArray(
				out,
				string.getBytes(StandardCharsets.UTF_8)
		);
	}

	static <T, R extends ResourceKey<Registry<T>>> @NotNull ResourceKey<T> readResourceKey(
			final @NotNull ByteBuf in,
			final @NotNull R registry
	) {
		return ResourceKey.create(
				registry,
				new ResourceLocation(readUtf8String(in))
		);
	}

	static void writeResourceKey(
			final @NotNull ByteBuf out,
			final @NotNull ResourceKey<?> resourceKey
	) {
		writeUtf8String(
				out,
				resourceKey.location().toString()
		);
	}
}
