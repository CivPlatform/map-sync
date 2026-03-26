package gjum.minecraft.mapsync.mod.net.packet;

import gjum.minecraft.mapsync.mod.data.CatchupChunk;
import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.buffers.BufferWriter;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * This is the final stage in the synchronisation process, sent in response to
 * a received {@link ClientboundChunkTimestampsResponsePacket}. Here you list
 * what chunks you'd like to receive from the server, who'll then respond with
 * a bunch of {@link ChunkTilePacket}.
 */
public class ServerboundCatchupRequestPacket implements Packet {
	public static final int PACKET_ID = 6;

	/**
	 * Chunks must all be in the same dimension
	 */
	public final List<CatchupChunk> chunks;

	/**
	 * Chunks must all be in the same dimension
	 */
	public ServerboundCatchupRequestPacket(@NotNull List<CatchupChunk> chunks) {
		if (chunks.isEmpty()) throw new Error("Chunks list must not be empty");
		ResourceKey<Level> dim = null;
		for (CatchupChunk chunk : chunks) {
			if (dim == null) dim = chunk.dimension();
			else if (!dim.equals(chunk.dimension())) {
				throw new Error("Chunks must all be in the same dimension " + dim + " but this one was " + chunk.dimension());
			}
		}
		this.chunks = chunks;
	}

	@Override
	public void write(@NotNull BufferWriter writer) throws Exception {
		writer.writeString(chunks.getFirst().dimension().identifier().toString());
		writer.writeUnt16(chunks.size());
		for (CatchupChunk chunk : chunks) {
			writer.writeInt32(chunk.chunk_x());
			writer.writeInt32(chunk.chunk_z());
			writer.writeInt64(chunk.timestamp());
		}
	}
}
