package gjum.minecraft.mapsync.common.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import static gjum.minecraft.mapsync.common.Utils.readStringFromBuf;
import static gjum.minecraft.mapsync.common.Utils.writeStringToBuf;

public record ChunkTile(
		ResourceKey<Level> dimension,
		int x, int z,
		int dataVersion,
		String dataHash,
		BlockColumn[] columns
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
		for (BlockColumn column : columns) {
			column.write(buf);
		}
	}

	public static ChunkTile fromBuf(ByteBuf buf) {
		String dimensionStr = readStringFromBuf(buf);
		var dimension = ResourceKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation(dimensionStr));
		int x = buf.readInt();
		int z = buf.readInt();
		int dataVersion = buf.readUnsignedShort();
		String hash = readStringFromBuf(buf);
		var columns = new BlockColumn[256];
		for (int i = 0; i < 256; i++) {
			columns[i] = BlockColumn.fromBuf(buf);
		}
		return new ChunkTile(dimension, x, z, dataVersion, hash, columns);
	}

	public static String computeDataHash(BlockColumn[] columns) {
		return ""; // XXX computeDataHash
	}
}
