package gjum.minecraft.mapsync.mod.data;

import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.NotNull;

public record RegionPos(
	int x,
	int z
) {
	public RegionPos(
		final @NotNull ChunkPos chunkPos
	) {
		this(
			chunkCoordToRegionCoord(chunkPos.x),
			chunkCoordToRegionCoord(chunkPos.z)
		);
	}

	/// Converts an absolute chunk coordinate to its region coordinate.
	public static int chunkCoordToRegionCoord(
		final int chunkCoord
	) {
		return chunkCoord >> 5;
	}

	/// Converts a region coordinate to its top-left most absolute chunk coordinate.
	public static int regionCoordToChunkCoord(
		final int regionCoord
	) {
		return regionCoord << 5;
	}

	public @NotNull ChunkPos toChunkPos() {
		return new ChunkPos(
			regionCoordToChunkCoord(this.x()),
			regionCoordToChunkCoord(this.z())
		);
	}
}
