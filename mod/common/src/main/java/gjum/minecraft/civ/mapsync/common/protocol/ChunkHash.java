package gjum.minecraft.civ.mapsync.common.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import static gjum.minecraft.civ.mapsync.common.Utils.readStringFromBuf;
import static gjum.minecraft.civ.mapsync.common.Utils.writeStringToBuf;

public record ChunkHash(
		ResourceKey<Level> dimension,
		int x, int z,
		int dataVersion,
		String dataHash
) {
	public ChunkPos chunkPos() {
		return new ChunkPos(x, z);
	}

	public void write(ByteBuf buf) {
		writeStringToBuf(buf, dimension.location().toString());
		buf.writeInt(x);
		buf.writeInt(z);
		buf.writeShort(dataVersion);
		writeStringToBuf(buf, dataHash);
	}

	public static ChunkHash fromBuf(ByteBuf buf) {
		String dimensionStr = readStringFromBuf(buf);
		var dimension = ResourceKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation(dimensionStr));
		int x = buf.readInt();
		int z = buf.readInt();
		int dataVersion = buf.readUnsignedShort();
		String dataHash = readStringFromBuf(buf);
		return new ChunkHash(dimension, x, z, dataVersion, dataHash);
	}
}
