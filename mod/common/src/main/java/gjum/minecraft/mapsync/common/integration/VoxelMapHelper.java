package gjum.minecraft.mapsync.common.integration;

import com.mamiyaotaru.voxelmap.interfaces.AbstractVoxelMap;
import com.mamiyaotaru.voxelmap.persistent.*;
import gjum.minecraft.mapsync.common.data.BlockInfo;
import gjum.minecraft.mapsync.common.data.ChunkTile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static gjum.minecraft.mapsync.common.Utils.getBiomeRegistry;

public class VoxelMapHelper {
	// TODO use mixins to access package-/private fields/methods

	private static Field cachedRegionsField;
	private static Field cachedRegionsPoolField;
	private static Field worldField;

	private static Field regionDataField;
	private static Field liveChunksUpdatedField;
	private static Field dataUpdatedField;
	private static Field regionLoadedField;
	private static Method regionLoadMethod;

	static {
		try {
			cachedRegionsField = PersistentMap.class.getDeclaredField("cachedRegions");
			cachedRegionsField.setAccessible(true);
			cachedRegionsPoolField = PersistentMap.class.getDeclaredField("cachedRegionsPool");
			cachedRegionsPoolField.setAccessible(true);
			worldField = PersistentMap.class.getDeclaredField("world");
			worldField.setAccessible(true);

			regionDataField = CachedRegion.class.getDeclaredField("data");
			regionDataField.setAccessible(true);
			liveChunksUpdatedField = CachedRegion.class.getDeclaredField("liveChunksUpdated");
			liveChunksUpdatedField.setAccessible(true);
			dataUpdatedField = CachedRegion.class.getDeclaredField("dataUpdated");
			dataUpdatedField.setAccessible(true);
			regionLoadedField = CachedRegion.class.getDeclaredField("loaded");
			regionLoadedField.setAccessible(true);
			regionLoadMethod = CachedRegion.class.getDeclaredMethod("load");
			regionLoadMethod.setAccessible(true);
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public static boolean isMapping() {
		if (worldField == null) return false;
		try {
			var vm = AbstractVoxelMap.getInstance();
			PersistentMap map = (PersistentMap) vm.getPersistentMap();
			var world = (ClientLevel) worldField.get(map);
			return world != null;
		} catch (IllegalAccessException ignored) {
			return false;
		}
	}

	// TODO update multiple chunks in one region at once
	// TODO which thread should this run on?
	public static boolean updateWithChunkTile(ChunkTile chunkTile) {
		try {
			if (!isMapping()) return false;

			int rx = chunkTile.x() >> 4;
			int rz = chunkTile.z() >> 4;
			var region = getRegion(rx, rz);

			var mapData = (CompressibleMapData) regionDataField.get(region);

			int x0 = (chunkTile.x() * 16) & 0xff;
			int z0 = (chunkTile.z() * 16) & 0xff;

			var biomeReg = getBiomeRegistry();

			int i = 0;
			for (int z = z0; z < z0 + 16; ++z) {
				for (int x = x0; x < x0 + 16; ++x) {
					var col = chunkTile.columns()[i++];

					mapData.setBiomeID(x, z, biomeReg.getId(col.biome()));

					int light = 0xf0 | col.light();
					mapData.setTransparentLight(x, z, light);
					mapData.setFoliageLight(x, z, light);
					mapData.setLight(x, z, light);
					mapData.setOceanFloorLight(x, z, light);

					setLayerStates(mapData, x, z, col.layers());
				}
			}

			liveChunksUpdatedField.set(region, true);
			dataUpdatedField.set(region, true);

			// render imagery
			region.refresh(false);

			return true;
		} catch (Throwable e) {
			e.printStackTrace();
			return false;
		}
	}

	private static final BlockInfo EMPTY = new BlockInfo(0, Blocks.AIR.defaultBlockState());

	private static void setLayerStates(CompressibleMapData mapData, int x, int z, List<BlockInfo> layers) {
		BlockInfo transparent = EMPTY;
		BlockInfo foliage = EMPTY;
		BlockInfo surface = EMPTY;
		BlockInfo seafloor = EMPTY;

		// XXX
		if (layers.size() > 1) transparent = layers.get(0);
		surface = layers.get(layers.size() - 1);

		mapData.setTransparentHeight(x, z, transparent.y());
		mapData.setTransparentBlockstate(x, z, transparent.state());
		mapData.setFoliageHeight(x, z, foliage.y());
		mapData.setFoliageBlockstate(x, z, foliage.state());
		mapData.setHeight(x, z, surface.y());
		mapData.setBlockstate(x, z, surface.state());
		mapData.setOceanFloorHeight(x, z, seafloor.y());
		mapData.setOceanFloorBlockstate(x, z, seafloor.state());
	}

	@NotNull
	private static CachedRegion getRegion(int rx, int rz)
			throws IllegalAccessException, InvocationTargetException {
		var vm = AbstractVoxelMap.getInstance();
		PersistentMap map = (PersistentMap) vm.getPersistentMap();

		@SuppressWarnings("unchecked")
		var cachedRegions = (ConcurrentHashMap<String, CachedRegion>) cachedRegionsField.get(map);

		@SuppressWarnings("unchecked")
		var cachedRegionsPool = (List<CachedRegion>) cachedRegionsPoolField.get(map);

		var world = (ClientLevel) worldField.get(map);

		String worldName = vm.getWaypointManager().getCurrentWorldName();
		String subWorldName = vm.getWaypointManager().getCurrentSubworldDescriptor(false);

		String key = rx + "," + rz;

		@Nullable CachedRegion region;

		// the following synchronized{} section matches VoxelMap's internal logic
		// see com.mamiyaotaru.voxelmap.persistent.PersistentMap.getRegions
		//noinspection SynchronizationOnLocalVariableOrMethodParameter
		synchronized (cachedRegions) {
			region = cachedRegions.get(key);
			// could be race condition if the region is not fully loaded at this point
			if (region == null || region instanceof EmptyCachedRegion) {
				region = new CachedRegion(map, key, world, worldName, subWorldName, rx, rz);

				cachedRegions.put(key, region);

				//noinspection SynchronizationOnLocalVariableOrMethodParameter
				synchronized (cachedRegionsPool) {
					cachedRegionsPool.add(region);
				}
			}
		}

		// TODO which thread should this run on?
		if (!((Boolean) regionLoadedField.get(region))) {
			regionLoadMethod.invoke(region);
		}

		return region;
	}
}
