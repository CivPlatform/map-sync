package gjum.minecraft.mapsync.mod.integrations.journeymap;

import gjum.minecraft.mapsync.mod.data.BlockColumn;
import gjum.minecraft.mapsync.mod.data.BlockInfo;
import gjum.minecraft.mapsync.mod.data.ChunkTile;
import journeymap.client.JourneymapClient;
import journeymap.client.io.FileHandler;
import journeymap.client.model.chunk.ChunkMD;
import journeymap.client.model.map.MapType;
import journeymap.client.model.region.RegionCoord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import static gjum.minecraft.mapsync.mod.MapSyncMod.logger;
import static gjum.minecraft.mapsync.mod.Utils.getBiomeRegistry;
import static gjum.minecraft.mapsync.mod.Utils.mc;

public class JourneyMapHelperReal {
	static boolean isMapping() {
		return JourneymapClient.getInstance().isMapping();
	}

	static boolean updateWithChunkTile(ChunkTile chunkTile) {
		if (!JourneymapClient.getInstance().isMapping()) return false; // BaseMapTask does this

		var renderController = JourneymapClient.getInstance().getChunkRenderController();
		if (renderController == null) return false;

		var chunkMd = new TileChunkMD(chunkTile);

		var rCoord = RegionCoord.fromChunkPos(
			FileHandler.getJMWorldDir(mc),
			MapType.day(chunkTile.dimension()), // type doesn't matter, only dimension is used
			chunkMd.getCoord().x,
			chunkMd.getCoord().z);

		final boolean renderedDay = renderWithDiagnostics(rCoord,
			MapType.day(chunkTile.dimension()), chunkMd, "day");
		final boolean renderedBiome = renderWithDiagnostics(rCoord,
			MapType.biome(chunkTile.dimension()), chunkMd, "biome");
		final boolean renderedTopo = renderWithDiagnostics(rCoord,
			MapType.topo(chunkTile.dimension()), chunkMd, "topo");

		if (!renderedDay || !renderedBiome || !renderedTopo) {
			logger.warn("[JourneyMap] chunk render debug {} -> day={}, biome={}, topo={}",
				chunkTile.chunkPos(), renderedDay, renderedBiome, renderedTopo);
		}

		return renderedDay && renderedBiome && renderedTopo;
	}

	private static boolean renderWithDiagnostics(
		RegionCoord rCoord,
		MapType mapType,
		TileChunkMD chunkMd,
		String mapName
	) {
		try {
			// keep this call in one place so failures are logged with context
			final boolean rendered = JourneymapClient.getInstance().getChunkRenderController().renderChunk(rCoord, mapType, chunkMd);
			return rendered;
		} catch (ChunkMD.ChunkMissingException e) {
			logger.error("[JourneyMap] Chunk missing for rendering {} at {}", mapName, chunkMd.chunkTile.chunkPos());
			return false;
		} catch (Exception t) {
			logger.error("[JourneyMap] Exception rendering {} at {}", mapName, chunkMd.chunkTile.chunkPos(), t);
			return false;
		}
	}

	/**
	 * References JourneyMap classes. Check {@link JourneyMapHelper#isJourneyMapNotAvailable} before referencing this.
	 */
	private static class TileChunkMD extends ChunkMD {
		private final ChunkTile chunkTile;

		public TileChunkMD(ChunkTile chunkTile) {
			super(new LevelChunk(mc.level, chunkTile.chunkPos()));
			this.chunkTile = chunkTile;
		}

		@Override
		public boolean hasChunk() {
			return true;
		}

		private BlockColumn getCol(int x, int z) {
			int xic = x & 0xf;
			int zic = z & 0xf;
			return chunkTile.columns()[xic + zic * 16];
		}

		private BlockColumn getCol(BlockPos pos) {
			return getCol(pos.getX(), pos.getZ());
		}

		@Override
		public BlockState getBlockState(BlockPos pos) {
			var column = getCol(pos.getX(), pos.getZ());
			if (column == null) return Blocks.AIR.defaultBlockState();
			var layers = column.layers();
			BlockInfo prevLayer = null;
			// note that layers are ordered top-down
			for (BlockInfo layer : layers) {
				if (layer.y() == pos.getY()) {
					return layer.state();
				}
				if (layer.y() < pos.getY()) {
					// top of layer is below pos, so pos is inside prevLayer
					if (prevLayer == null) return Blocks.AIR.defaultBlockState(); // first layer is already below pos
					return prevLayer.state();
				}
				prevLayer = layer;
			}
			if (layers.isEmpty()) return Blocks.AIR.defaultBlockState();
			return layers.get(layers.size() - 1).state();
		}

		@Override
		public BlockState getChunkBlockState(BlockPos pos) {
			return getBlockState(pos);
		}

		@Override
		public boolean canBlockSeeTheSky(int localX, int y, int localZ) {
			return getSavedLightValue(localX, y, localZ) > 0;
		}


		@Override
		public int getSavedLightValue(int localX, int y, int localZ) {
			var column = getCol(localX, localZ);
			if (column == null) return 0;
			int light = column.light();
			if (light < 0 || light > 15) return 0;
			return light;
		}

		@Override
		public int getPrecipitationHeight(BlockPos pos) {
			return this.getPrecipitationHeight(pos.getX(), pos.getZ());
		}

		@Override
		public ResourceKey<Level> getDimension() {
			return chunkTile.dimension();
		}

		@Override
		public int getPrecipitationHeight(int localX, int localZ) {
			var column = getCol(localX, localZ);
			if (column == null || column.layers().isEmpty()) {
				return mc.level != null ? this.getMinY() : 0;
			}
			return column.layers().get(0).y();
		}

		@Override
		public int getHeight(BlockPos pos) {
			return this.getPrecipitationHeight(pos);
		}

		@Override
		public Holder<Biome> getBiomeHolder(BlockPos pos) {
			var biome = getBiome(pos);
			if (biome == null || mc.level == null) return null;
			var biomeKey = getBiomeRegistry().getResourceKey(biome).orElse(null);
			if (biomeKey == null) return null;
			return mc.level.registryAccess()
				.lookupOrThrow(Registries.BIOME)
				.getOrThrow(biomeKey);
		}

		@Override
		public Biome getBiome(BlockPos pos) {
			var column = getCol(pos);
			if (column != null) return column.biome();
			return null;
		}
	}
}
