package gjum.minecraft.mapsync.common.integrations.xaerosmap;

import gjum.minecraft.mapsync.common.data.BlockColumn;
import gjum.minecraft.mapsync.common.data.BlockInfo;
import gjum.minecraft.mapsync.common.data.ChunkTile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelChunkTicks;
import org.jetbrains.annotations.NotNull;
import xaero.map.MapProcessor;
import xaero.map.MapWriter;
import xaero.map.WorldMapSession;
import xaero.map.region.MapBlock;
import xaero.map.region.MapRegion;
import xaero.map.region.MapTile;
import xaero.map.region.MapTileChunk;
import xaero.map.world.MapWorld;

import java.lang.reflect.Field;

import static gjum.minecraft.mapsync.common.MapSyncMod.debugLog;
import static gjum.minecraft.mapsync.common.Utils.getBiomeRegistry;
import static gjum.minecraft.mapsync.common.Utils.mc;

public class XaerosWorldMapHelperReal {

	static boolean isMapping() {
		try {
			return WorldMapSession.getCurrentSession().isUsable();
		} catch (Exception e) {
			debugLog("CurrentSession is null probably");
			return false;
		}
	}

	static boolean updateWithChunkTile(ChunkTile chunkTile) {
		if (isMapping()) {
			MapProcessor mapProcessor = WorldMapSession.getCurrentSession().getMapProcessor();
			MapWriter mapWriter = mapProcessor.getMapWriter();
			MapWorld mapWorld = mapProcessor.getMapWorld();
			int caveLayer = mapProcessor.getCurrentCaveLayer();

			int chunkX = chunkTile.x();
			int chunkZ = chunkTile.z();

			int tileChunkX = chunkX >> 2;
			int tileChunkZ = chunkZ >> 2;

			// = 8 -> 8x8 chunk = 1 region
			int rx = tileChunkX >> 3;
			int rz = tileChunkZ >> 3;

			int tileX = chunkX & 3;
			int tileZ = chunkZ & 3;

			MapTile mapTile = mapProcessor.getTilePool().get(mapProcessor.getCurrentDimension(), tileX, tileZ);

			MapRegion region = mapWorld.getCurrentDimension().getLayeredMapRegions().getLeaf(caveLayer, rx, rz);
			if (region == null) {
				String dimId = mapWorld.getCurrentDimensionId().identifier().toString().replace(':', '_').replace('/', '_');
				String safeMwId = mapWorld.getFutureMultiworldUnsynced().replace(':', '_').replace('/', '_');
				region = new MapRegion(
					mapWorld.getMainId(),
					dimId,
					safeMwId,
					mapWorld.getCurrentDimension(),
					rx,
					rz,
					caveLayer,
					0, // version from private config API
					!mapWorld.getCurrentDimension().isUsingWorldSave(),
					getBiomeRegistry()
				);
				mapWorld.getCurrentDimension().getLayeredMapRegions().putLeaf(rx, rz, region);
			}

			int localTileChunkX = tileChunkX & 7;
			int localTileChunkZ = tileChunkZ & 7;

			MapTileChunk tileChunk = region.getChunk(localTileChunkX, localTileChunkZ); //local
			if (tileChunk == null) {
				tileChunk = new MapTileChunk(region, tileChunkX, tileChunkZ); //non-local
				region.setChunk(localTileChunkX, localTileChunkZ, tileChunk); //local
			}

			LevelChunk levelChunk = buildLevelChunkFromChunkTile(chunkTile, mc.level);

			BlockColumn[] columns = chunkTile.columns();
			int worldBottomY = mapProcessor.getWorld().getMinY();
			int worldTopY = mapProcessor.getWorld().getMaxY();

			synchronized(mapProcessor.renderThreadPauseSync) {
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						int idx = (z << 4) | x;

						MapBlock currentPixel = mapTile.isLoaded() ? mapTile.getBlock(x, z) : null;

						MapBlock loadingPixel = new MapBlock();

						BlockColumn col = columns[idx];

						ResourceKey<@NotNull Biome> biomeKey = getBiomeRegistry().getResourceKey(col.biome()).orElse(null);

						// TODO
						boolean cave = false;
						boolean fullCave = false;
						boolean flowers = false; // From private 'Xaero's lib fabric'

						mapWriter.loadPixel(
							mc.level,
							mapProcessor.getWorldBlockRegistry(),
							loadingPixel,
							currentPixel,
							levelChunk,
							x,
							z,
							worldTopY,
							worldBottomY, // Bottom non-air block
							cave,
							fullCave,
							col.layers().getLast().y(),
							mapTile.wasWrittenOnce(),
							mapProcessor.getMapWorld().isIgnoreHeightmaps(),
							getBiomeRegistry(),
							flowers,
							worldBottomY,
							new BlockPos.MutableBlockPos()
						);

						loadingPixel.setBiome(biomeKey);

						mapTile.setBlock(x, z, loadingPixel);
					}
				}

			}

			mapTile.setLoaded(true);
			mapTile.setWorldInterpretationVersion(MapTile.CURRENT_WORLD_INTERPRETATION_VERSION);
			// If cave info available
			// mapTile.setWrittenCave(caveStart, caveDepth);

			tileChunk.setTile(tileX, tileZ, mapTile, mapProcessor.getBlockStateShortShapeCache());
			tileChunk.setHasHadTerrain();
			tileChunk.setChanged(true);
			tileChunk.setLoadState((byte)2);
			tileChunk.setToUpdateBuffers(true);

			return true;
		} else {
			return false;
		}
	}

	private static LevelChunk buildLevelChunkFromChunkTile(ChunkTile chunkTile, Level level) {
		ChunkPos pos = new ChunkPos(chunkTile.x(), chunkTile.z());
		int sectionCount = level.getSectionsCount();
		int minSection = level.getMinSectionY();
		LevelChunkSection[] sections = new LevelChunkSection[sectionCount];


		BlockColumn[] columns = chunkTile.columns();

		for (int i = 0; i < sectionCount; i++) {
			// Creating empty containers
			PalettedContainer<BlockState> blockStates = level.palettedContainerFactory().createForBlockStates();
			PalettedContainer<Holder<Biome>> biomes = level.palettedContainerFactory().createForBiomes();

			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {

					int idx = (z << 4) | x;
					BlockColumn col = columns[idx];

					var biomeKey = getBiomeRegistry().getResourceKey(col.biome()).orElse(null);
					if (biomeKey == null) continue;
					Holder<Biome> biomeHolder = level.registryAccess()
						.lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
						.getOrThrow(biomeKey);

					for (BlockInfo layer : col.layers()) {
						int y = layer.y();
						if ((y >> 4) - minSection == i) {
							// Fill containers
							BlockState state = layer.state();
							blockStates.set(x, y & 15, z, state);
							biomes.set(x >> 2, (y & 15) >> 2, z >> 2, biomeHolder);
						}
					}
				}
			}
			sections[i] = new LevelChunkSection(blockStates, biomes);
		}

		// Create chunk
		LevelChunkTicks<Block> blockTicks = new LevelChunkTicks<>();
		LevelChunkTicks<Fluid> fluidTicks = new LevelChunkTicks<>();
		UpgradeData upgradeData = UpgradeData.EMPTY;
		long inhabitedTime = 0L;
		BlendingData blendingData = null;

		LevelChunk chunk = new LevelChunk(
				level,
				pos,
				upgradeData,
				blockTicks,
				fluidTicks,
				inhabitedTime,
				sections,
				null, // PostLoadProcessor
				blendingData
		);

		return chunk;
	}
}
