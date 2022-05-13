package gjum.minecraft.mapsync.common.integration;

import gjum.minecraft.mapsync.common.protocol.*;
import journeymap.client.JourneymapClient;
import journeymap.client.io.FileHandler;
import journeymap.client.model.*;
import journeymap.common.nbt.RegionData;
import journeymap.common.nbt.RegionDataStorageHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;

import static gjum.minecraft.mapsync.common.Utils.getBiomeRegistry;
import static gjum.minecraft.mapsync.common.Utils.mc;

public class JourneyMapHelper {
	public static void updateWithChunkTile(ChunkTile chunkTile) {
		var renderController = JourneymapClient.getInstance().getChunkRenderController();
		if (renderController == null) return;

		var chunkMd = new TileChunkMD(chunkTile);

		var rCoord = RegionCoord.fromChunkPos(
				FileHandler.getJMWorldDir(mc),
				MapType.day(chunkTile.dimension()), // type doesn't matter, only dimension is used
				chunkMd.getCoord().x,
				chunkMd.getCoord().z);

		var key = new RegionDataStorageHandler.Key(rCoord, MapType.day(chunkTile.dimension()));
		RegionData regionData = RegionDataStorageHandler.getInstance().getRegionData(key);

		final boolean renderedDay = renderController.renderChunk(rCoord,
				MapType.day(chunkTile.dimension()), chunkMd, regionData);
		if (!renderedDay) System.out.println("Failed rendering day at " + chunkTile.chunkPos());

		final boolean renderedBiome = renderController.renderChunk(rCoord,
				MapType.biome(chunkTile.dimension()), chunkMd, regionData);
		if (!renderedBiome) System.out.println("Failed rendering biome at " + chunkTile.chunkPos());

		final boolean renderedTopo = renderController.renderChunk(rCoord,
				MapType.topo(chunkTile.dimension()), chunkMd, regionData);
		if (!renderedTopo) System.out.println("Failed rendering topo at " + chunkTile.chunkPos());
	}

	private static class TileChunkMD extends NBTChunkMD {
		private final ChunkTile chunkTile;

		public TileChunkMD(ChunkTile chunkTile) {
			super(new LevelChunk(Minecraft.getInstance().level, chunkTile.chunkPos()),
					chunkTile.chunkPos(),
					null, // all accessing methods are overridden
					MapType.day(chunkTile.dimension()) // just has to not be `underground`
			);
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
			var layers = getCol(pos.getX(), pos.getZ()).layers();
			// XXX index from y
			for (BlockInfo layer : layers) {
				if (layer.y() == pos.getY()) {
					return layer.state();
				}
			}
			return getLast(layers).state();
		}

		@Override
		public Integer getGetLightValue(BlockPos pos) {
			return getCol(pos.getX(), pos.getZ()).light();
		}

		@Override
		public Integer getTopY(BlockPos pos) {
			return getCol(pos.getX(), pos.getZ()).layers().get(0).y();
		}

		@Override
		public int getHeight(BlockPos pos) {
			return this.getTopY(pos);
		}

		@Override
		public Biome getBiome(BlockPos pos) {
			return getCol(pos).biome(getBiomeRegistry());
		}
	}

	private static <T> T getLast(List<T> l) {
		if (l.isEmpty()) {
			throw new Error("Empty list");
		}
		return l.get(l.size() - 1);
	}
}
