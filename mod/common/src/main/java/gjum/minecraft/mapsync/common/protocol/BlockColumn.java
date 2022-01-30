package gjum.minecraft.mapsync.common.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
		// make sure there's at most 127 elements, and the last element is the bottom layer
		var layersLimited = layers.stream().limit(127)
				.collect(Collectors.toCollection(() ->
						new ArrayList<>(Math.min(127, layers.size()))));
		// last layer may be before 127; will be first and only entry most of the time
		layersLimited.set(layersLimited.size() - 1, layers.get(layers.size() - 1));
		buf.writeByte(layersLimited.size());
		for (BlockInfo layer : layersLimited) {
			layer.write(buf);
		}
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
