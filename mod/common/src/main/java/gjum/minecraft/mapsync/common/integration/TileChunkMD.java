package gjum.minecraft.mapsync.common.integration;

import gjum.minecraft.mapsync.common.data.*;
import journeymap.client.model.MapType;
import journeymap.client.model.NBTChunkMD;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import static gjum.minecraft.mapsync.common.Utils.mc;

/**
 * References JourneyMap classes. Check {@link JourneyMapHelper#isJourneyMapNotAvailable} before referencing this.
 */
class TileChunkMD extends NBTChunkMD {
	private final ChunkTile chunkTile;

	public TileChunkMD(ChunkTile chunkTile) {
		super(new LevelChunk(mc.level, chunkTile.chunkPos()),
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
		BlockInfo prevLayer = null;
		// note that layers are ordered top-down
		for (BlockInfo layer : layers) {
			if (layer.y() == pos.getY()) {
				return layer.state();
			}
			if (layer.y() < pos.getY()) {
				// top of layer is below pos, so pos is inside prevLayer
				if (prevLayer == null) return null; // first layer is already below pos
				return prevLayer.state();
			}
			prevLayer = layer;
		}
		if (layers.isEmpty()) return Blocks.AIR.defaultBlockState();
		return layers.get(layers.size() - 1).state();
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
		return getCol(pos).biome();
	}
}
