package gjum.minecraft.mapsync.common.integrations.xaerosmap;

import gjum.minecraft.mapsync.common.data.ChunkTile;

import static gjum.minecraft.mapsync.common.MapSyncMod.debugLog;

public class XaerosWorldMapHelper {
	public static boolean isXaerosWorldMapNotAvailable;

	static {
		try {
			// TODO: update when found needed
			Class.forName("xaero.map.WorldMap");
			isXaerosWorldMapNotAvailable = false;
		} catch (NoClassDefFoundError | ClassNotFoundException ignored) {
			isXaerosWorldMapNotAvailable = true;
		}
	}

	public static boolean isMapping() {
		if (isXaerosWorldMapNotAvailable) return false;
		return XaerosWorldMapHelperReal.isMapping();
	}

	public static boolean updateWithChunkTile(ChunkTile chunkTile) {
		if (isXaerosWorldMapNotAvailable) return false;
		return XaerosWorldMapHelperReal.updateWithChunkTile(chunkTile);
	}
}
