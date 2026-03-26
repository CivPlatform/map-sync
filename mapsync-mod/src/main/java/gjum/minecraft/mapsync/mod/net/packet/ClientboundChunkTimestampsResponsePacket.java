package gjum.minecraft.mapsync.mod.net.packet;

import gjum.minecraft.mapsync.mod.data.CatchupChunk;
import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.buffers.BufferReader;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * You'll receive this in response to a sent {@link ServerboundChunkTimestampsRequestPacket},
 * containing an elaboration of chunk timestamps of all the regions you listed.
 * You should respond with a {@link ServerboundCatchupRequestPacket}.
 */
public class ClientboundChunkTimestampsResponsePacket implements Packet {
	public static final int PACKET_ID = 5;

	/**
	 * sorted by newest to oldest
	 */
	public final @NotNull List<CatchupChunk> chunks;

	public ClientboundChunkTimestampsResponsePacket(@NotNull List<CatchupChunk> chunks) {
		this.chunks = chunks;
	}

	public static Packet read(BufferReader reader) throws Exception {
		final ResourceKey<Level> dimension = reader.readResourceKey(Registries.DIMENSION);

		int length = reader.readUnt16();
		List<CatchupChunk> chunks = new ArrayList<>(length);
		for (int i = 0; i < length; i++) {
			int chunk_x = reader.readInt32();
			int chunk_z = reader.readInt32();
			long timestamp = reader.readInt64();
			CatchupChunk chunk = new CatchupChunk(
					dimension, chunk_x, chunk_z, timestamp);
			chunks.add(chunk);
		}
		return new ClientboundChunkTimestampsResponsePacket(chunks);
	}
}
