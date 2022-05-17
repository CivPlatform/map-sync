package gjum.minecraft.mapsync.common.net;

import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

public abstract class Packet {
	public abstract void write(ByteBuf buf);

	@Nullable
	protected static String readString(@NotNull ByteBuf in) {
		final int length = in.readInt();
		if (length <= 0) return null;
		final byte[] bytes = new byte[length];
		in.readBytes(bytes);
		return new String(bytes);
	}

	protected static void writeString(@NotNull ByteBuf out, @Nullable String string) {
		if (string == null || string.isEmpty()) {
			out.writeInt(0);
			return;
		}
		final byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
		out.writeInt(bytes.length);
		out.writeBytes(bytes);
	}

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

	@NotNull
	protected static PublicKey readKey(ByteBuf in) {
		try {
			X509EncodedKeySpec keySpec = new X509EncodedKeySpec(readByteArray(in));
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			return keyFactory.generatePublic(keySpec);
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			throw new RuntimeException(e);
		}
	}
}
