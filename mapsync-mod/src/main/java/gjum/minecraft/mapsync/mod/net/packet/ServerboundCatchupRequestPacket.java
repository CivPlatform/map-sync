package gjum.minecraft.mapsync.mod.net.packet;

import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.buffers.BufferReader;
import gjum.minecraft.mapsync.mod.net.buffers.BufferWriter;
import gjum.minecraft.mapsync.mod.utils.Assertions;
import gjum.minecraft.mapsync.mod.utils.MagicValues;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import org.apache.commons.lang3.IntegerRange;
import org.jetbrains.annotations.NotNull;

/// The client sends this in response to a [ClientboundChunkTimestampsResponsePacket], requesting the server to send
/// chunk-tile data for each of the specified chunks within a specified region. The server may respond by sending a
/// [ChunkTilePacket] for each chunk.
///
/// - Prev: [ClientboundChunkTimestampsResponsePacket]
/// - Next: [ChunkTilePacket]
public record ServerboundCatchupRequestPacket(
	@NotNull Identifier dimension,
	short regionX,
	short regionZ,
	@NotNull Map<@NotNull ChunkPos, @NotNull Long> chunks
) implements Packet {
	public static final int PACKET_ID = 6;

	public ServerboundCatchupRequestPacket {
		Assertions.assertNotNull(dimension);
		chunks = Assertions.assertNonNullMap(chunks);
		Assertions.assertIntRange(IntegerRange.of(1, MagicValues.REGION_GRID), chunks.size());
	}

	public static @NotNull Packet read(
		final @NotNull BufferReader reader
	) throws Exception {
		final Identifier dimension = reader.readIdentifier();
		final short regionX = (short) reader.readInt16();
		final short regionZ = (short) reader.readInt16();
		final int count = reader.readUnt10() + 1;
		final int anchorX = regionX << 5;
		final int anchorZ = regionZ << 5;
		final Map<ChunkPos, Long> chunks = new HashMap<>(count);
		for (int i = 0; i < count; i++) {
			final int localX = reader.readUnt5();
			final int localZ = reader.readUnt5();
			final long timestamp = reader.readInt64();
			chunks.put(new ChunkPos(anchorX + localX, anchorZ + localZ), timestamp);
		}
		return new ServerboundCatchupRequestPacket(dimension, regionX, regionZ, chunks);
	}

	@Override
	public void write(
		final @NotNull BufferWriter writer
	) throws Exception {
		writer.writeString(this.dimension().toString());
		writer.writeInt16(this.regionX());
		writer.writeInt16(this.regionZ());
		writer.writeUnt10(this.chunks().size() - 1);
		for (final var entry : this.chunks().entrySet()) {
			final ChunkPos chunkPos = entry.getKey();
			writer.writeUnt5(chunkPos.getRegionLocalX());
			writer.writeUnt5(chunkPos.getRegionLocalZ());
			writer.writeInt64(entry.getValue());
		}
	}
}
