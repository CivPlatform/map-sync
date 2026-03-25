package gjum.minecraft.mapsync.mod.data;

import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.utils.Assertions;
import gjum.minecraft.mapsync.mod.utils.MagicValues;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public record ChunkTile(
		ResourceKey<Level> dimension,
		int x, int z,
		long timestamp,
		int dataVersion,
		byte[] dataHash,
		BlockColumn[] columns
) {
	public ChunkTile {
		Assertions.assertNotNull("dataHash", dataHash);
		Assertions.assertLength("dataHash", dataHash.length, MagicValues.SHA1_HASH_LENGTH);
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
		final ResourceKey<Level> dimension = Packet.readResourceKey(buf, Registries.DIMENSION);
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
}
