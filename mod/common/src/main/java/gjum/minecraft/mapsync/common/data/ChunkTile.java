package gjum.minecraft.mapsync.common.data;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static gjum.minecraft.mapsync.common.Utils.readStringFromBuf;
import static gjum.minecraft.mapsync.common.Utils.writeStringToBuf;

public record ChunkTile(
		ResourceKey<Level> dimension,
		int x, int z,
		int dataVersion,
		byte[] dataHash,
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
		buf.writeInt(dataHash.length); // TODO could be Short as hash length is known to be small
		buf.writeBytes(dataHash);
	}

	public static void writeColumns(BlockColumn[] columns, ByteBuf buf) {
		// TODO compress
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
		byte[] hash = new byte[buf.readInt()];
		buf.readBytes(hash);
		var columns = new BlockColumn[256];
		for (int i = 0; i < 256; i++) {
			columns[i] = BlockColumn.fromBuf(buf);
		}
		return new ChunkTile(dimension, x, z, dataVersion, hash, columns);
	}

	public static byte[] computeDataHash(ByteBuf columns) {
		try {
			// SHA-1 is faster than SHA-256, and other algorithms are not required to be implemented in every JVM
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			md.update(columns.array());
			return md.digest();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return new byte[]{};
		}
	}
}
