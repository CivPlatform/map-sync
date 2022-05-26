package gjum.minecraft.mapsync.common.integration;

import gjum.minecraft.mapsync.common.data.ChunkTile;

public class JourneyMapHelper {
	public static boolean isJourneyMapNotAvailable;

	static {
		try {
			Class.forName("journeymap.client.JourneymapClient");
			isJourneyMapNotAvailable = false;
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
