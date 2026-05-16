package gjum.minecraft.mapsync.mod.net.packet;

import gjum.minecraft.mapsync.mod.data.CatchupChunk;
import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.buffers.BufferReader;
import gjum.minecraft.mapsync.mod.net.buffers.BufferWriter;
import gjum.minecraft.mapsync.mod.utils.Assertions;
import gjum.minecraft.mapsync.mod.utils.MagicValues;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.IntegerRange;
import org.jetbrains.annotations.NotNull;

/// The server will send this packet, containing an elaboration of chunk timestamps of a particular region as requested
/// via [ServerboundChunkTimestampsRequestPacket]. The client should respond with a [ServerboundCatchupRequestPacket]
/// if it finds any chunks with a timestamp newer than its own.
///
/// - Prev: [ServerboundChunkTimestampsRequestPacket]
/// - Next: [ServerboundCatchupRequestPacket]
public record ClientboundChunkTimestampsResponsePacket(
	@NotNull List<@NotNull CatchupChunk> chunks
) implements Packet {
	public static final int PACKET_ID = 5;

	public ClientboundChunkTimestampsResponsePacket {
		chunks = Assertions.assertNonNullList(chunks);
		Assertions.assertIntRange(IntegerRange.of(1, MagicValues.REGION_GRID), chunks.size());
	}

	public static @NotNull ClientboundChunkTimestampsResponsePacket read(
		final @NotNull BufferReader reader
	) throws Exception {
		final ResourceKey<Level> dimension = reader.readResourceKey(Registries.DIMENSION);
		final int anchorChunkX = reader.readInt16() << 5;
		final int anchorChunkZ = reader.readInt16() << 5;
		final var chunks = new CatchupChunk[reader.readUnt10() + 1];
		for (int i = 0; i < chunks.length; i++) {
			chunks[i] = new CatchupChunk(
				dimension,
				anchorChunkX + reader.readUnt5(),
				anchorChunkZ + reader.readUnt5(),
				reader.readInt64()
			);
		}
		return new ClientboundChunkTimestampsResponsePacket(
			List.of(chunks)
		);
	}

	/// All chunks in this packet must share a dimension and a region (region =
	/// chunk coords >> 5). The server constructs packets that way; this write
	/// asserts it loudly so a bug in packet assembly can't silently corrupt
	/// the wire format.
	@Override
	public void write(
		final @NotNull BufferWriter writer
	) throws Exception {
		final CatchupChunk anchor = this.chunks().getFirst();
		final ResourceKey<Level> dimension = anchor.dimension();
		final int regionX = anchor.chunk_x() >> 5;
		final int regionZ = anchor.chunk_z() >> 5;
		for (final CatchupChunk chunk : this.chunks()) {
			if (!chunk.dimension().equals(dimension)
				|| (chunk.chunk_x() >> 5) != regionX
				|| (chunk.chunk_z() >> 5) != regionZ) {
				throw new IllegalStateException(
					"Catchup chunks in a single response must share dimension and region: anchor="
						+ dimension.identifier() + " r(" + regionX + "," + regionZ + ") vs chunk "
						+ chunk.dimension().identifier() + " (" + chunk.chunk_x() + "," + chunk.chunk_z() + ")"
				);
			}
		}
		writer.writeString(dimension.identifier().toString());
		writer.writeInt16((short) regionX);
		writer.writeInt16((short) regionZ);
		writer.writeUnt10(this.chunks().size() - 1);
		for (final CatchupChunk chunk : this.chunks()) {
			writer.writeUnt5(chunk.chunk_x() & 31);
			writer.writeUnt5(chunk.chunk_z() & 31);
			writer.writeInt64(chunk.timestamp());
		}
	}
}
