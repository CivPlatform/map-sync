package gjum.minecraft.mapsync.common.net.packet;

import gjum.minecraft.mapsync.common.data.CatchupChunk;
import gjum.minecraft.mapsync.common.net.Packet;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

import static gjum.minecraft.mapsync.common.Utils.readStringFromBuf;

/**
 * You'll receive this in response to a sent {@link ServerboundChunkTimestampsRequestPacket},
 * containing an elaboration of chunk timestamps of all the regions you listed.
 * You should respond with a {@link ServerboundCatchupRequestPacket}.
 */
public class ClientboundChunkTimestampsResponsePacket extends Packet {
	public static final int PACKET_ID = 5;

	/**
	 * sorted by newest to oldest
	 */
	public final @Nonnull List<CatchupChunk> chunks;

	public ClientboundChunkTimestampsResponsePacket(@Nonnull List<CatchupChunk> chunks) {
		this.chunks = chunks;
	}

	public static Packet read(ByteBuf buf) {
		String dimensionStr = readStringFromBuf(buf);
		var dimension = ResourceKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation(dimensionStr));

		int length = buf.readInt();
		List<CatchupChunk> chunks = new ArrayList<>(length);
		for (int i = 0; i < length; i++) {
			int chunk_x = buf.readInt();
			int chunk_z = buf.readInt();
			long timestamp = buf.readLong();
			CatchupChunk chunk = new CatchupChunk(
					dimension, chunk_x, chunk_z, timestamp);
			chunks.add(chunk);
		}
		return new ClientboundChunkTimestampsResponsePacket(chunks);
	}

	@Override
	public void write(ByteBuf buf) {
		throw new Error("Can't be sent from client");
	}
}
