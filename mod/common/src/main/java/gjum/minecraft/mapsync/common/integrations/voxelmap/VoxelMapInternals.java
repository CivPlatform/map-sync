package gjum.minecraft.mapsync.common.integrations.voxelmap;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.WaypointManager;
import com.mamiyaotaru.voxelmap.persistent.CachedRegion;
import com.mamiyaotaru.voxelmap.persistent.CompressibleMapData;
import com.mamiyaotaru.voxelmap.persistent.EmptyCachedRegion;
import com.mamiyaotaru.voxelmap.persistent.PersistentMap;
import gjum.minecraft.mapsync.common.data.BlockColumn;
import gjum.minecraft.mapsync.common.data.BlockInfo;
import gjum.minecraft.mapsync.common.data.ChunkTile;
import gjum.minecraft.mapsync.common.mixins.voxelmap.CachedRegionAccessor;
import gjum.minecraft.mapsync.common.mixins.voxelmap.PersistentMapAccessor;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class VoxelMapInternals {
	static boolean isMapping() {
		return ((PersistentMapAccessor) VoxelConstants.getVoxelMapInstance().getPersistentMap()).mapsync$getWorld() != null;
	}

	// TODO update multiple chunks in one region at once
	// TODO which thread should this run on?
	static boolean updateWithChunkTile(
		final @NotNull ChunkTile chunkTile
	) {
		final PersistentMap map = VoxelConstants.getVoxelMapInstance().getPersistentMap();
		final var mapAccessor = (PersistentMapAccessor) map;
		final ClientLevel currentLevel = mapAccessor.mapsync$getWorld();
		if (currentLevel == null) {
			return false;
		}

		@Nullable CachedRegion cachedRegion; {
			final WaypointManager waypointManager = VoxelConstants.getVoxelMapInstance().getWaypointManager();
			final String worldName = waypointManager.getCurrentWorldName();
			final String subWorldName = waypointManager.getCurrentSubworldDescriptor(false);

			final int regionX = chunkTile.x() >> 4;
			final int regionZ = chunkTile.z() >> 4;
			final String regionKey = regionX + "," + regionZ;

			final ConcurrentHashMap<String, CachedRegion> cachedRegions = mapAccessor.mapsync$getCachedRegions();
			synchronized (cachedRegions) {
				cachedRegion = cachedRegions.get(regionKey);
				// could be race condition if the region is not fully loaded at this point
				if (cachedRegion == null || cachedRegion instanceof EmptyCachedRegion) {
					cachedRegions.put(regionKey, cachedRegion = new CachedRegion(
						map,
						regionKey,
						currentLevel,
						worldName,
						subWorldName,
						regionX,
						regionZ
					));

					final List<CachedRegion> cachedRegionsPool = mapAccessor.mapsync$getCachedRegionsPool();
					synchronized (cachedRegionsPool) {
						cachedRegionsPool.add(cachedRegion);
					}
				}
			}
		}

		final var regionAccessor = (CachedRegionAccessor) cachedRegion;
		if (!regionAccessor.mapsync$isLoaded()) {
			regionAccessor.mapsync$load();
		}

		final ReentrantLock lock = regionAccessor.mapsync$getThreadLock(); lock.lock(); try {
			final CompressibleMapData data = regionAccessor.mapsync$getData();

			final int x0 = (chunkTile.x() << 4) & 0xFF;
			final int z0 = (chunkTile.z() << 4) & 0xFF;

			int i = 0;
			for (int z = z0; z < z0 + 16; ++z) for (int x = x0; x < x0 + 16; ++x) {
				final BlockColumn blockColumn = chunkTile.columns()[i++];
				if (blockColumn.layers().isEmpty()) {
					continue;
				}

				data.setBiome(x, z, blockColumn.biome());

				final int light = 0xF0 | blockColumn.light();
				data.setLight(x, z, light);
				data.setTransparentLight(x, z, light);
				data.setFoliageLight(x, z, light);
				data.setOceanFloorLight(x, z, light);

				BlockInfo transparent = newAirBlock();
				BlockInfo foliage = newAirBlock();
				BlockInfo surface = newAirBlock();
				BlockInfo seafloor = newAirBlock();

				final List<BlockInfo> blockColumnLayers = blockColumn.layers();

				// XXX
				final BlockInfo zerothBlock = blockColumnLayers.getFirst();
				if (blockColumnLayers.size() > 1) {
					transparent = zerothBlock;
				}
				surface = blockColumnLayers.getLast();
				// trees hack
				if (zerothBlock.state().is(BlockTags.LEAVES)) {
					surface = zerothBlock;
				}

				data.setTransparentHeight(x, z, transparent.y());
				data.setTransparentBlockstate(x, z, transparent.state());
				data.setFoliageHeight(x, z, foliage.y());
				data.setFoliageBlockstate(x, z, foliage.state());
				data.setHeight(x, z, surface.y());
				data.setBlockstate(x, z, surface.state());
				data.setOceanFloorHeight(x, z, seafloor.y());
				data.setOceanFloorBlockstate(x, z, seafloor.state());
			}

			regionAccessor.mapsync$setLiveChunksUpdated(true);
			regionAccessor.mapsync$setDataUpdated(true);

			// render imagery
			cachedRegion.refresh(false);
		}
		finally {
			lock.unlock();
		}
		return true;
	}

	private static @NotNull BlockInfo newAirBlock() {
		return new BlockInfo(0, Blocks.AIR.defaultBlockState());
	}
}
