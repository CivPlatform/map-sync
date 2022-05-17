package gjum.minecraft.mapsync.common.data;

import io.netty.buffer.ByteBuf;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;

import static gjum.minecraft.mapsync.common.Utils.getBiomeRegistry;

public record BlockColumn(
		Biome biome,
		int light,
		List<BlockInfo> layers
) {
	public void write(ByteBuf buf) {
		buf.writeShort(getBiomeRegistry().getId(biome));
		buf.writeByte(light);
		// write at most 127 layers, and always include the bottom layer
		buf.writeByte(Math.min(127, layers.size()));
		int i = 0;
		for (BlockInfo layer : layers) {
			if (++i == 127) break;
			layer.write(buf);
		}
		if (i == 127) layers.get(layers.size() - 1).write(buf);
	}

	public static BlockColumn fromBuf(ByteBuf buf) {
		int biomeId = buf.readUnsignedShort();
		Biome biome = getBiomeRegistry().byId(biomeId);
		int light = buf.readUnsignedByte();
		int numLayers = buf.readUnsignedByte();
		var layers = new ArrayList<BlockInfo>(numLayers);
		for (int i = 0; i < numLayers; i++) {
			layers.add(BlockInfo.fromBuf(buf));
		}
		return new BlockColumn(biome, light, layers);
	}
}
