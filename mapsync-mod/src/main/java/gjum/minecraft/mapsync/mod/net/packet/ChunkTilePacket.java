package gjum.minecraft.mapsync.mod.net.packet;

import gjum.minecraft.mapsync.mod.data.ChunkTile;
import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.buffers.BufferReader;
import gjum.minecraft.mapsync.mod.net.buffers.BufferWriter;
import org.jetbrains.annotations.NotNull;

/**
 * This packet is sent in two situations:
 *
 * 1. Clients are relaying chunk data to each other in real time.
 *
 * 2. You have requested synchronisation via {@link ServerboundCatchupRequestPacket}.
 */
public class ChunkTilePacket implements Packet {
	public static final int PACKET_ID = 4;

	public final ChunkTile chunkTile;

	public ChunkTilePacket(@NotNull ChunkTile chunkTile) {
		this.chunkTile = chunkTile;
	}

	public static Packet read(BufferReader reader) throws Exception {
		return new ChunkTilePacket(
				ChunkTile.read(reader));
	}

	@Override
	public void write(@NotNull BufferWriter writer) throws Exception {
		chunkTile.write(writer);
	}
}
