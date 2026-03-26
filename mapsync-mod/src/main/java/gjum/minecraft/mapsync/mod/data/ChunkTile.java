package gjum.minecraft.mapsync.mod.data;

import gjum.minecraft.mapsync.mod.net.buffers.BufferReader;
import gjum.minecraft.mapsync.mod.net.buffers.BufferWriter;
import gjum.minecraft.mapsync.mod.utils.Assertions;
import gjum.minecraft.mapsync.mod.utils.MagicValues;
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
		Assertions.assertNotNull(dataHash);
		Assertions.assertLength(dataHash.length, MagicValues.SHA1_HASH_LENGTH);
	}

	public ChunkPos chunkPos() {
		return new ChunkPos(x, z);
	}

	public void write(BufferWriter writer) throws Exception {
		writeMetadata(writer);
		writeColumns(columns, writer);
	}

	/**
	 * without columns
	 */
	public void writeMetadata(BufferWriter writer) throws Exception {
		writer.writeString(dimension.identifier().toString());
		writer.writeInt32(x);
		writer.writeInt32(z);
		writer.writeInt64(timestamp);
		writer.writeUnt16(dataVersion);
		writer.writeBytes(dataHash);
	}

	public static void writeColumns(BlockColumn[] columns, BufferWriter writer) throws Exception {
		// TODO compress
		for (BlockColumn column : columns) {
			column.write(writer);
		}
	}

	public static ChunkTile read(BufferReader reader) throws Exception {
		final ResourceKey<Level> dimension = reader.readResourceKey(Registries.DIMENSION);
		int x = reader.readInt32();
		int z = reader.readInt32();
		long timestamp = reader.readInt64();
		int dataVersion = reader.readUnt16();
		byte[] hash = reader.readBytesOfLength(MagicValues.SHA1_HASH_LENGTH);
		var columns = new BlockColumn[256];
		for (int i = 0; i < 256; i++) {
			columns[i] = BlockColumn.read(reader);
		}
		return new ChunkTile(dimension, x, z, timestamp, dataVersion, hash, columns);
	}
}
