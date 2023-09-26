package gjum.minecraft.mapsync.common.net;

import gjum.minecraft.mapsync.common.net.packet.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ClientboundPacketDecoder extends ReplayingDecoder<Void> {
	public static @Nullable Packet constructServerPacket(int id, ByteBuf buf) {
		if (id == ChunkTilePacket.PACKET_ID) return ChunkTilePacket.read(buf);
		if (id == ClientboundEncryptionRequestPacket.PACKET_ID) return ClientboundEncryptionRequestPacket.read(buf);
		if (id == ClientboundChunkTimestampsResponsePacket.PACKET_ID) return ClientboundChunkTimestampsResponsePacket.read(buf);
		if (id == ClientboundRegionTimestampsPacket.PACKET_ID) return ClientboundRegionTimestampsPacket.read(buf);
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
