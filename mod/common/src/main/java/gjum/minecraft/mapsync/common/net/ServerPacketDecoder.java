package gjum.minecraft.mapsync.common.net;

import gjum.minecraft.mapsync.common.net.packet.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ServerPacketDecoder extends ReplayingDecoder<Void> {
	public static @Nullable Packet constructServerPacket(int id, ByteBuf buf) {
		if (id == ChunkTilePacket.PACKET_ID) return ChunkTilePacket.read(buf);
		if (id == SEncryptionRequest.PACKET_ID) return SEncryptionRequest.read(buf);
		if (id == SCatchup.PACKET_ID) return SCatchup.read(buf);
		if (id == SRegionTimestamps.PACKET_ID) return SRegionTimestamps.read(buf);
		return null;
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf buf, List<Object> out) {
		try {
			byte id = buf.readByte();
			final Packet packet = constructServerPacket(id, buf);
			if (packet == null) {
				SyncClient.logger.error("[ServerPacketDecoder] " +
						"Unknown server packet id " + id + " 0x" + Integer.toHexString(id));
				ctx.close();
				return;
			}
			out.add(packet);
		} catch (Throwable err) {
			err.printStackTrace();
			ctx.close();
		}
	}
}
