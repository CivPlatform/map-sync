package gjum.minecraft.civ.mapsync.common.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import static java.nio.charset.StandardCharsets.UTF_8;

public record ChunkTile(
		ResourceKey<Level> dimension,
		int x, int z,
		int version,
		BlockColumn[] columns
) {
	public void write(ByteBuf buf) {
		String dimensionStr = dimension.location().toString();
		buf.writeShort(dimensionStr.length());
		buf.writeCharSequence(dimensionStr, UTF_8);
		buf.writeInt(x);
		buf.writeInt(z);
		buf.writeShort(version);
		for (BlockColumn column : columns) {
			column.write(buf);
		}
	}

	public static ChunkTile fromBuf(ByteBuf buf) {
		int dimensionStrLen = buf.readUnsignedShort();
		String dimensionStr = buf.readCharSequence(dimensionStrLen, UTF_8).toString();
		var dimension = ResourceKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation(dimensionStr));
		int x = buf.readInt();
		int z = buf.readInt();
		var columns = new BlockColumn[256];
		int version = buf.readUnsignedShort();
		for (int i = 0; i < 256; i++) {
			columns[i] = BlockColumn.fromBuf(buf);
		}
		return new ChunkTile(dimension, x, z, version, columns);
	}

	public static ChunkTile fromLevel(Level level, int cx, int cz) {
		var dimension = level.dimension();
		var chunk = level.getChunk(cx, cz);
		var biomeRegistry = level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);

		var columns = new BlockColumn[256];
		var pos = new BlockPos.MutableBlockPos(0, 0, 0);
		int i = 0;
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				pos.set(x, 0, z);
				columns[i++] = BlockColumn.fromChunk(chunk, pos, biomeRegistry);
			}
		}

		int version = 1;

		return new ChunkTile(dimension, cx, cz, version, columns);
	}
}
