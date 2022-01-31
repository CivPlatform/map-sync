package gjum.minecraft.mapsync.common.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
		writeMetadata(buf);
		writeColumns(columns, buf);
	}

	/**
	 * without columns
	 */
	public void writeMetadata(ByteBuf buf) {
		writeStringToBuf(buf, dimension.location().toString());
		buf.writeInt(x);
		buf.writeInt(z);
		buf.writeShort(dataVersion);
		writeStringToBuf(buf, dataHash);
	}

	public static void writeColumns(BlockColumn[] columns, ByteBuf buf) {
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

	// could be sped up a little by using a static (shared) upper-bound size buffer and marking method as synchronized, since we only call this method once at a time
	public static String computeDataHash(ByteBuf columns) {
		try {
			// SHA-1 is faster than SHA-256, and other algorithms are not required to be implemented in every JVM
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			md.update(columns.array());
			return new BigInteger(md.digest()).toString(16);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return "";
		}
	}
}
