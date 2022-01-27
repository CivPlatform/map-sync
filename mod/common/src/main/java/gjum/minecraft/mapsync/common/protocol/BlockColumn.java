package gjum.minecraft.mapsync.common.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

public record BlockColumn(
		int biomeId,
		int light,
		List<BlockInfo> layers
) {
	public Biome biome(Registry<Biome> biomeRegistry) {
		return biomeRegistry.byId(biomeId);
	}

	public void write(ByteBuf buf) {
		buf.writeShort(biomeId);
		buf.writeByte(light);
		buf.writeShort(layers.size());
		for (BlockInfo layer : layers) {
			layer.write(buf);
		}
	}

	public static BlockColumn fromBuf(ByteBuf buf) {
		int biomeId = buf.readUnsignedShort();
		int light = buf.readUnsignedByte();
		int numLayers = buf.readShort();
		var layers = new ArrayList<BlockInfo>(numLayers);
		for (int i = 0; i < numLayers; i++) {
			layers.add(BlockInfo.fromBuf(buf));
		}
		return new BlockColumn(biomeId, light, layers);
	}

	public static BlockColumn fromChunk(LevelChunk chunk, MutableBlockPos pos, Registry<Biome> biomeRegistry) {
		var layers = new ArrayList<BlockInfo>();
		int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
		pos.setY(y);
		var bs = chunk.getBlockState(pos);
		while (true) {
			layers.add(new BlockInfo(pos.getY(), bs));
			if (bs.getMaterial().isSolidBlocking()) break;
			var prevBS = bs;
			do {
				pos.setY(--y);
				bs = chunk.getBlockState(pos);
			} while (bs == prevBS || bs.isAir());
		}

		int light = chunk.getLightEmission(pos);
		var biome = Minecraft.getInstance().level.getBiome(pos);
		int biomeId = biomeRegistry.getId(biome);
		return new BlockColumn(biomeId, light, layers);
	}
}
