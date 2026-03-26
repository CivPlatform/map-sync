package gjum.minecraft.mapsync.mod.data;

import static gjum.minecraft.mapsync.mod.Utils.getBiomeRegistry;

import gjum.minecraft.mapsync.mod.net.buffers.BufferReader;
import gjum.minecraft.mapsync.mod.net.buffers.BufferWriter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.biome.Biome;

public record BlockColumn(
		Biome biome,
		int light,
		List<BlockInfo> layers
) {
	public void write(BufferWriter writer) throws Exception {
		writer.writeUnt16(getBiomeRegistry().getId(biome));
		writer.writeUnt8(light);
		// write at most 127 layers, and always include the bottom layer
		writer.writeUnt8(Math.min(127, layers.size()));
		int i = 0;
		for (BlockInfo layer : layers) {
			if (++i == 127) break;
			layer.write(writer);
		}
		if (i == 127) layers.getLast().write(writer);
	}

	public static BlockColumn read(BufferReader reader) throws Exception {
		int biomeId = reader.readUnt16();
		Biome biome = getBiomeRegistry().byId(biomeId);
		int light = reader.readUnt8();
		int numLayers = reader.readUnt8();
		var layers = new ArrayList<BlockInfo>(numLayers);
		for (int i = 0; i < numLayers; i++) {
			layers.add(BlockInfo.read(reader));
		}
		return new BlockColumn(biome, light, layers);
	}
}
