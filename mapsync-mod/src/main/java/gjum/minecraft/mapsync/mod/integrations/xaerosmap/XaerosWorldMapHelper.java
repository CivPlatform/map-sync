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

	/// Whether Xaero has finished scanning its region cache directory for the
	/// current dimension. The backfill runner waits on this before iterating
	/// regions, otherwise it would race against Xaero's own startup probe and
	/// miss files that hadn't been registered yet.
	public static boolean hasDoneRegionDetection() {
		if (isXaerosWorldMapNotAvailable) return false;
		return XaerosWorldMapHelperReal.hasDoneRegionDetection();
	}

	/// Iterates every region Xaero has detected on disk for the current
	/// dimension, exposing the region coords and the cache file's mtime to
	/// the callback. Returns the number of regions visited (0 when Xaero
	/// isn't installed, not yet mapping, or hasn't finished detection).
	public static int iterateExistingRegions(
		final ExistingRegionConsumer callback
	) {
		if (isXaerosWorldMapNotAvailable) return 0;
		return XaerosWorldMapHelperReal.iterateExistingRegions(callback);
	}

	/// Region-iteration callback. Coords are in Xaero's region-coordinate
	/// space, which matches MapSync's (one region = 32x32 chunks).
	@FunctionalInterface
	public interface ExistingRegionConsumer {
		void accept(int regionX, int regionZ, long mtimeMillis);
	}
}
