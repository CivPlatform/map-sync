package gjum.minecraft.mapsync.common;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

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
}
