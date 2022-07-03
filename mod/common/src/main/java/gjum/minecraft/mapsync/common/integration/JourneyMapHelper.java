package gjum.minecraft.mapsync.common.integration;

import gjum.minecraft.mapsync.common.data.ChunkTile;

public class JourneyMapHelper {
	public static boolean isJourneyMapNotAvailable;

	static {
		try {
			Class.forName("journeymap.client.JourneymapClient");
			try {
				// check it's a recent version
				Class.forName("journeymap.client.model.NBTChunkMD");
				isJourneyMapNotAvailable = false;
			} catch (NoClassDefFoundError | ClassNotFoundException ignored2) {
				isJourneyMapNotAvailable = true;
				System.err.println("Please update JourneyMap to at least 5.8.3");
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
