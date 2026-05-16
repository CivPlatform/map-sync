package gjum.minecraft.mapsync.mod.integrations.voxelmap;

import gjum.minecraft.mapsync.mod.data.ChunkTile;
import net.minecraft.world.level.ChunkPos;
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

	/// TODO probe the actual VoxelMap region cache for this chunk. Until
	/// that's wired up, fail safe to "yes, VoxelMap has data" when the mod
	/// is loaded — never silently overwrite a VoxelMap user's tiles. Users
	/// who want to backfill can uncheck "Preserve existing map data" in
	/// the MapSync GUI for a session.
	public static boolean hasExistingChunkData(
		@SuppressWarnings("unused") final @NotNull ChunkPos chunkPos
	) {
		return isModAvailable;
	}
}
