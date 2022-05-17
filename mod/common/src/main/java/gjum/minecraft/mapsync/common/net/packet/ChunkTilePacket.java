package gjum.minecraft.mapsync.common.net.packet;

import gjum.minecraft.mapsync.common.data.ChunkTile;
import gjum.minecraft.mapsync.common.net.Packet;
import io.netty.buffer.ByteBuf;

import javax.annotation.Nonnull;

public class ChunkTilePacket extends Packet {
	public static final int PACKET_ID = 4;

	public final ChunkTile chunkTile;

	public ChunkTilePacket(@Nonnull ChunkTile chunkTile) {
		this.chunkTile = chunkTile;
	}

	public static Packet read(ByteBuf buf) {
		return new ChunkTilePacket(
				ChunkTile.fromBuf(buf));
	}

	@Override
	public void write(ByteBuf buf) {
		chunkTile.write(buf);
	}
}
