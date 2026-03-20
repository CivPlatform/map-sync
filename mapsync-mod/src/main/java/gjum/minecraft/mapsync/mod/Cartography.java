package gjum.minecraft.mapsync.mod;

import gjum.minecraft.mapsync.mod.data.BlockColumn;
import gjum.minecraft.mapsync.mod.data.BlockInfo;
import gjum.minecraft.mapsync.mod.data.ChunkTile;
import gjum.minecraft.mapsync.mod.utils.Shortcuts;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.security.MessageDigest;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

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
		final byte[] dataHash; {
			final ByteBuf columnsBuf = Unpooled.buffer();
			ChunkTile.writeColumns(columns, columnsBuf);
			final MessageDigest md = Shortcuts.shaHash();
			md.update(columnsBuf.nioBuffer());
			dataHash = md.digest();
		}

		return new ChunkTile(dimension, cx, cz, timestamp, dataVersion, dataHash, columns);
	}

	public static BlockColumn blockColumnFromChunk(LevelChunk chunk, BlockPos.MutableBlockPos pos) {
		var layers = new ArrayList<BlockInfo>();
		int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
		int minBuildHeight = chunk.getMinY();
		pos.setY(y);
		var bs = chunk.getBlockState(pos);
		do {
			layers.add(new BlockInfo(pos.getY(), bs));
			if (bs.canOcclude()) break; // TODO: Check that this is valid
			var prevBS = bs;
			do {
				pos.setY(--y);
				bs = chunk.getBlockState(pos);
			} while ((bs == prevBS || bs.isAir()) && y >= -4096);
		} while (y >= minBuildHeight);
		var world = Minecraft.getInstance().level;
		int light = world.getBrightness(LightLayer.BLOCK, pos);
		var biome = chunk.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2).value();
		return new BlockColumn(biome, light, layers);
	}
}
