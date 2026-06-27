package gjum.minecraft.mapsync.mod.integrations.voxelmap;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.VoxelMap;
import com.mamiyaotaru.voxelmap.WaypointManager;
import com.mamiyaotaru.voxelmap.persistent.CachedRegion;
import com.mamiyaotaru.voxelmap.persistent.CompressibleMapData;
import com.mamiyaotaru.voxelmap.persistent.EmptyCachedRegion;
import com.mamiyaotaru.voxelmap.persistent.PersistentMap;
import gjum.minecraft.mapsync.mod.data.BlockColumn;
import gjum.minecraft.mapsync.mod.data.BlockInfo;
import gjum.minecraft.mapsync.mod.data.ChunkTile;
import gjum.minecraft.mapsync.mod.data.DimensionKey;
import gjum.minecraft.mapsync.mod.mixins.voxelmap.CachedRegionAccessor;
import gjum.minecraft.mapsync.mod.mixins.voxelmap.PersistentMapAccessor;
import gjum.minecraft.mapsync.mod.utils.MagicValues;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

final class VoxelMapInternals {
	static boolean isMapping() {
		return ((PersistentMapAccessor) VoxelConstants.getVoxelMapInstance().getPersistentMap()).mapsync$getWorld() != null;
	}

	// TODO update multiple chunks in one region at once
	// TODO which thread should this run on?
	static boolean updateWithChunkTile(
		final @NotNull ChunkTile chunkTile
	) {
		final VoxelMap mod = VoxelConstants.getVoxelMapInstance();
		if (!(mod.getPersistentMap() instanceof final PersistentMap map)) {
			return false;
		}

		final var mapAccessor = (PersistentMapAccessor) map;
		final ClientLevel currentLevel = mapAccessor.mapsync$getWorld();
		if (!DimensionKey.matches(currentLevel, chunkTile.dimension())) {
			return false;
		}

		final CachedRegion cachedRegion = getCachedRegion(
			mod.getWaypointManager(),
			map,
			mapAccessor,
			currentLevel,
			chunkTile.x(),
			chunkTile.z()
		);

		final var regionAccessor = (CachedRegionAccessor) cachedRegion;
		if (!cachedRegion.isLoaded()) {
			regionAccessor.mapsync$load();
		}

		final ReentrantLock lock = regionAccessor.mapsync$getThreadLock(); lock.lock(); try {
			renderChunk(cachedRegion, regionAccessor, chunkTile);
		}
		finally {
			lock.unlock();
		}
		return true;
	}

	/// @see PersistentMap#doProcessChunk
	private static @NotNull CachedRegion getCachedRegion(
		final @NotNull WaypointManager waypointManager,
		final @NotNull PersistentMap map,
		final @NotNull PersistentMapAccessor mapAccessor,
		final @NotNull ClientLevel currentLevel,
		final int chunkX,
		final int chunkZ
	) {
		// Do NOT switch this to RegionPos: VoxelMap uses 16x16 regions, not Minecraft's 32x32 regions!
		final int regionX = chunkX >> 4, regionZ = chunkZ >> 4;
		final String regionKey = regionX + "," + regionZ;

		final ConcurrentHashMap<String, CachedRegion> cachedRegions = mapAccessor.mapsync$getCachedRegions();
		synchronized (cachedRegions) {
			CachedRegion cachedRegion = cachedRegions.get(regionKey);
			// could be race condition if the region is not fully loaded at this point
			if (cachedRegion == null || cachedRegion instanceof EmptyCachedRegion) {
				cachedRegions.put(regionKey, cachedRegion = new CachedRegion(
					map,
					regionKey,
					currentLevel,
					waypointManager.getCurrentWorldName(),
					waypointManager.getCurrentSubworldDescriptor(false),
					regionX,
					regionZ
				));

				final List<CachedRegion> cachedRegionsPool = mapAccessor.mapsync$getCachedRegionsPool();
				synchronized (cachedRegionsPool) {
					cachedRegionsPool.add(cachedRegion);
				}
			}
			return cachedRegion;
		}
	}

	/// @see CachedRegion#doLoadChunkData
	private static void renderChunk(
		final @NotNull CachedRegion cachedRegion,
		final @NotNull CachedRegionAccessor regionAccessor,
		final @NotNull ChunkTile chunk
	) {
		final CompressibleMapData data = cachedRegion.getMapData();

		// Converts the absolute chunk pos to a VoxelMap-region internal block pos (0..255)
		final int
			minBlockX = (chunk.x() << 4) & MagicValues.UNT8_MASK,
			maxBlockX = minBlockX + MagicValues.CHUNK_AXIS,
			minBlockZ = (chunk.z() << 4) & MagicValues.UNT8_MASK,
			maxBlockZ = minBlockZ + MagicValues.CHUNK_AXIS;

		final BlockColumn[] columns = chunk.columns();
		int i = 0;
		for (int z = minBlockZ; z < maxBlockZ; ++z) for (int x = minBlockX; x < maxBlockX; ++x) {
			final BlockColumn blockColumn = columns[i++];
			renderBlockColumn(data, x, z, blockColumn);
		}

		regionAccessor.mapsync$setIsEmpty(false);
		regionAccessor.mapsync$setLiveChunksUpdated(true);
		regionAccessor.mapsync$setDataUpdated(true);

		// render imagery
		cachedRegion.refresh(false);
	}

	/// @see PersistentMap#getAndStoreData
	private static void renderBlockColumn(
		final @NotNull CompressibleMapData data,
		final int blockX,
		final int blockZ,
		final @NotNull BlockColumn column
	) {
		final List<BlockInfo> layers = column.layers();
		if (layers.isEmpty()) {
			return;
		}

		data.setBiome(blockX, blockZ, column.biome());

		final int light = 0xF0 | column.light();
		data.setLight(blockX, blockZ, light);
		data.setTransparentLight(blockX, blockZ, light);
		data.setFoliageLight(blockX, blockZ, light);
		data.setOceanFloorLight(blockX, blockZ, light);

		BlockInfo transparent = newAirBlock();
		BlockInfo foliage = newAirBlock();
		BlockInfo surface = newAirBlock();
		BlockInfo seafloor = newAirBlock();

		// XXX
		final BlockInfo zerothBlock = layers.getFirst();
		if (layers.size() > 1) {
			transparent = zerothBlock;
		}
		surface = layers.getLast();
		// trees hack
		if (zerothBlock.state().is(BlockTags.LEAVES)) {
			surface = zerothBlock;
		}

		data.setTransparentHeight(blockX, blockZ, transparent.y());
		data.setTransparentBlockstate(blockX, blockZ, transparent.state());
		data.setFoliageHeight(blockX, blockZ, foliage.y());
		data.setFoliageBlockstate(blockX, blockZ, foliage.state());
		data.setHeight(blockX, blockZ, surface.y());
		data.setBlockstate(blockX, blockZ, surface.state());
		data.setOceanFloorHeight(blockX, blockZ, seafloor.y());
		data.setOceanFloorBlockstate(blockX, blockZ, seafloor.state());
	}

	private static @NotNull BlockInfo newAirBlock() {
		return new BlockInfo(0, Blocks.AIR.defaultBlockState());
	}
}
