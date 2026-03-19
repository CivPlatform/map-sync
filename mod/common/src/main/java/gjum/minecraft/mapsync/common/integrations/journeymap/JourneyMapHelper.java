package gjum.minecraft.mapsync.common.integrations.journeymap;

import java.util.regex.Pattern;

import gjum.minecraft.mapsync.common.data.ChunkTile;

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
}
