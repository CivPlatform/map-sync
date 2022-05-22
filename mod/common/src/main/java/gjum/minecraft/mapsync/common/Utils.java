package gjum.minecraft.mapsync.common;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Utils {
	public static final Minecraft mc = Minecraft.getInstance();

	public static Registry<Biome> getBiomeRegistry() {
		return Minecraft.getInstance().level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
	}

	private static HashMap<String, Long> lastTimeSeenError = new HashMap<>();

	public static void printErrorRateLimited(@NotNull Throwable e) {
		try {
			final long now = System.currentTimeMillis();
			final String key = e.getMessage();
			if (lastTimeSeenError.getOrDefault(key, 0L) > now - 10000L) return;
			lastTimeSeenError.put(key, now);
			e.printStackTrace();
		} catch (Throwable e2) {
			e2.printStackTrace();
		}
	}

	public static void writeStringToBuf(@NotNull ByteBuf buf, @Nullable String string) {
		if (string == null || string.isEmpty()) {
			buf.writeInt(0);
			return;
		}
		final byte[] bytes = string.getBytes(UTF_8);
		buf.writeInt(bytes.length);
		buf.writeBytes(bytes);
	}

	public static @NotNull String readStringFromBuf(@NotNull ByteBuf buf) {
		int length = buf.readInt();
		final byte[] bytes = new byte[length];
		buf.readBytes(bytes);
		return new String(bytes, UTF_8);
	}
}
