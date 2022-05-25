package gjum.minecraft.mapsync.common;

import gjum.minecraft.mapsync.common.data.*;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;

public class Cartography {
	public static ChunkTile chunkTileFromLevel(Level level, int cx, int cz) {
		long timestamp = System.currentTimeMillis();
		var dimension = level.dimension();
		var chunk = level.getChunk(cx, cz);

		var columns = new BlockColumn[256];
		var pos = new BlockPos.MutableBlockPos(0, 0, 0);
		int i = 0;
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				pos.set(x, 0, z);
				columns[i++] = blockColumnFromChunk(chunk, pos);
			}
		}
		int dataVersion = 1;

		// TODO speedup: don't serialize twice (once here, once later when writing to network)
		var columnsBuf = Unpooled.buffer();
		ChunkTile.writeColumns(columns, columnsBuf);
		byte[] dataHash = ChunkTile.computeDataHash(columnsBuf);

		return new ChunkTile(dimension, cx, cz, timestamp, dataVersion, dataHash, columns);
	}

	public static BlockColumn blockColumnFromChunk(LevelChunk chunk, BlockPos.MutableBlockPos pos) {
		var layers = new ArrayList<BlockInfo>();
		int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
		int minBuildHeight = chunk.getLevel().getMinBuildHeight();
		pos.setY(y);
		var bs = chunk.getBlockState(pos);
		do {
			layers.add(new BlockInfo(pos.getY(), bs));
			if (bs.getMaterial().isSolidBlocking()) break;
			var prevBS = bs;
			do {
				pos.setY(--y);
				bs = chunk.getBlockState(pos);
			} while ((bs == prevBS || bs.isAir()) && y >= -4096);
		} while (y >= minBuildHeight);
		var world = Minecraft.getInstance().level;
		int light = world.getBrightness(LightLayer.BLOCK, pos);
		var biome = chunk.getNoiseBiome(pos.getX(), pos.getY(), pos.getZ()).value();
		return new BlockColumn(biome, light, layers);
	}
}
