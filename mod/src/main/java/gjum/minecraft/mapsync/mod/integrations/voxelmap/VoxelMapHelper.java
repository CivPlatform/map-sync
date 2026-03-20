package gjum.minecraft.mapsync.mod.integrations.voxelmap;

import gjum.minecraft.mapsync.mod.data.ChunkTile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public final class VoxelMapHelper {
	@ApiStatus.Internal
	public static boolean isModAvailable = false;

	public static boolean isMapping() {
		return isModAvailable && VoxelMapInternals.isMapping();
	}

	public static boolean updateWithChunkTile(
		final @NotNull ChunkTile chunkTile
	) {
		return isModAvailable && VoxelMapInternals.updateWithChunkTile(chunkTile);
	}
}
