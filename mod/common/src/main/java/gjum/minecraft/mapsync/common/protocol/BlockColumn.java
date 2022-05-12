package gjum.minecraft.mapsync.common.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;

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
		int light = buf.readUnsignedByte();
		int numLayers = buf.readUnsignedByte();
		var layers = new ArrayList<BlockInfo>(numLayers);
		for (int i = 0; i < numLayers; i++) {
			layers.add(BlockInfo.fromBuf(buf));
		}
		return new BlockColumn(biomeId, light, layers);
	}
}
