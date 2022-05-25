package gjum.minecraft.mapsync.common.data;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public record CatchupChunk(
		ResourceKey<Level> dimension,
		int chunk_x, int chunk_z,
		long timestamp
) {
	/**
	 * calculates square of Euclidean distance to the other chunk
	 */
	public int getDistanceSq(ChunkPos other) {
		int dx = other.x - chunk_x;
		int dz = other.z - chunk_z;
		return dx * dx + dz * dz;
	}

	public ChunkPos chunkPos() {
		return new ChunkPos(chunk_x, chunk_z);
	}
}
