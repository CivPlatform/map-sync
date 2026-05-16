package gjum.minecraft.mapsync.mod.integrations.journeymap;

import gjum.minecraft.mapsync.mod.data.ChunkTile;
import java.util.regex.Pattern;
import net.minecraft.world.level.ChunkPos;

public class JourneyMapHelper {
	public static boolean isJourneyMapNotAvailable;

	static {
		try {
			Class<?> jmClient = Class.forName("journeymap.client.JourneymapClient");
			String version = null;
			try {
				// Try to get a version field or method
				try {
					version = (String) jmClient.getDeclaredField("FULL_VERSION").get(null);
				} catch (NoSuchFieldException e) {  }
			} catch (Exception ignored) {}

			if (version != null) {
				// Compare version strings as needed, e.g., "6.0.0"
				if (!Pattern.compile("6\\.\\d+\\.\\d+").matcher(version).find()) {
					isJourneyMapNotAvailable = true;
					System.err.println("Please update JourneyMap to at least 6.0.0 (found " + version + ")");
				} else {
					isJourneyMapNotAvailable = false;
				}
			}
		} catch (NoClassDefFoundError | ClassNotFoundException ignored) {
			isJourneyMapNotAvailable = true;
		}
	}

	public static boolean isMapping() {
		if (isJourneyMapNotAvailable) return false;
		return JourneyMapHelperReal.isMapping();
	}

	public static boolean updateWithChunkTile(ChunkTile chunkTile) {
		if (isJourneyMapNotAvailable) return false;
		return JourneyMapHelperReal.updateWithChunkTile(chunkTile);
	}

	/// TODO probe the actual JourneyMap region files for this chunk. Until
	/// that's wired up, fail safe to "yes, JM has data" when the mod is
	/// loaded — never silently overwrite a JM user's tiles. Users who want
	/// to backfill can uncheck "Preserve existing map data" in the MapSync
	/// GUI for a session.
	public static boolean hasExistingChunkData(
		@SuppressWarnings("unused") final ChunkPos chunkPos
	) {
		return !isJourneyMapNotAvailable;
	}
}
