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
}
