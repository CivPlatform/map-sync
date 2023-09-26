package gjum.minecraft.mapsync.common.net.packet;

import gjum.minecraft.mapsync.common.data.CatchupChunk;
import gjum.minecraft.mapsync.common.net.Packet;
import io.netty.buffer.ByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import java.util.List;

import static gjum.minecraft.mapsync.common.Utils.writeStringToBuf;

/**
 * This is the final stage in the synchronisation process, sent in response to
 * a received {@link ClientboundChunkTimestampsResponsePacket}. Here you list
 * what chunks you'd like to receive from the server, who'll then respond with
 * a bunch of {@link ChunkTilePacket}.
 */
public class ServerboundCatchupRequestPacket extends Packet {
	public static final int PACKET_ID = 6;

	/**
	 * Chunks must all be in the same dimension
	 */
	public final List<CatchupChunk> chunks;

	/**
	 * Chunks must all be in the same dimension
	 */
	public ServerboundCatchupRequestPacket(@Nonnull List<CatchupChunk> chunks) {
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
	public void write(ByteBuf buf) {
		writeStringToBuf(buf, chunks.get(0).dimension().location().toString());
		buf.writeInt(chunks.size());
		for (CatchupChunk chunk : chunks) {
			buf.writeInt(chunk.chunk_x());
			buf.writeInt(chunk.chunk_z());
			buf.writeLong(chunk.timestamp());
		}
	}
}
