package gjum.minecraft.mapsync.mod.integrations.xaerosmap;

import gjum.minecraft.mapsync.mod.data.ChunkTile;
import net.minecraft.world.level.ChunkPos;

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

	/// Whether Xaero's World Map already has data for this chunk. Used by the
	/// preserve-existing-map-data safeguard to avoid overwriting a tile the
	/// player explored before MapSync was installed. Returns false when the
	/// mod isn't loaded — there's nothing to preserve.
	public static boolean hasExistingChunkData(final ChunkPos chunkPos) {
		if (isXaerosWorldMapNotAvailable) return false;
		return XaerosWorldMapHelperReal.hasExistingChunkData(chunkPos);
	}
}
