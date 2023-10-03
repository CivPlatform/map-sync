package gjum.minecraft.mapsync.common.data;

import gjum.minecraft.mapsync.common.net.Packet;
import gjum.minecraft.mapsync.common.utils.Arguments;
import gjum.minecraft.mapsync.common.utils.MagicValues;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public record ChunkTile(
		ResourceKey<Level> dimension,
		int x, int z,
		long timestamp,
		int dataVersion,
		byte[] dataHash,
		BlockColumn[] columns
) {
	public ChunkTile {
		Arguments.checkNotNull("dataHash", dataHash);
		Arguments.checkLength("dataHash", dataHash.length, MagicValues.SHA1_HASH_LENGTH);
	}

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
		Packet.writeResourceKey(buf, dimension);
		buf.writeInt(x);
		buf.writeInt(z);
		buf.writeLong(timestamp);
		buf.writeShort(dataVersion);
		buf.writeBytes(dataHash);
	}

	public static void writeColumns(BlockColumn[] columns, ByteBuf buf) {
		// TODO compress
		for (BlockColumn column : columns) {
			column.write(buf);
		}
	}

	public static ChunkTile fromBuf(ByteBuf buf) {
		var dimension = Packet.readResourceKey(buf, Registry.DIMENSION_REGISTRY);
		int x = buf.readInt();
		int z = buf.readInt();
		long timestamp = buf.readLong();
		int dataVersion = buf.readUnsignedShort();
		byte[] hash = Packet.readByteArrayOfSize(buf, MagicValues.SHA1_HASH_LENGTH);
		var columns = new BlockColumn[256];
		for (int i = 0; i < 256; i++) {
			columns[i] = BlockColumn.fromBuf(buf);
		}
		return new ChunkTile(dimension, x, z, timestamp, dataVersion, hash, columns);
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
