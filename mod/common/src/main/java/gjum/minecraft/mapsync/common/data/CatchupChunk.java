package gjum.minecraft.mapsync.common.data;

import gjum.minecraft.mapsync.common.net.SyncClient;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public final class CatchupChunk {
	private final ResourceKey<Level> dimension;
	private final int chunk_x;
	private final int chunk_z;
	private final long timestamp;
	/**
	 * where to request it from
	 */
	public SyncClient syncClient;

	public CatchupChunk(
			ResourceKey<Level> dimension,
			int chunk_x, int chunk_z,
			long timestamp
	) {
		this.dimension = dimension;
		this.chunk_x = chunk_x;
		this.chunk_z = chunk_z;
		this.timestamp = timestamp;
	}

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

	public ResourceKey<Level> dimension() {
		return dimension;
	}

	public int chunk_x() {
		return chunk_x;
	}

	public int chunk_z() {
		return chunk_z;
	}

	public long timestamp() {
		return timestamp;
	}
}
