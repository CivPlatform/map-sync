package gjum.minecraft.mapsync.mod.net;

import gjum.minecraft.mapsync.mod.net.buffers.BufferReader;
import gjum.minecraft.mapsync.mod.net.packet.ChunkTilePacket;
import gjum.minecraft.mapsync.mod.net.packet.ClientboundChunkTimestampsResponsePacket;
import gjum.minecraft.mapsync.mod.net.packet.ClientboundEncryptionRequestPacket;
import gjum.minecraft.mapsync.mod.net.packet.ClientboundRegionTimestampsPacket;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public class ClientboundPacketDecoder extends ReplayingDecoder<Void> {
	public static @Nullable Packet constructServerPacket(int id, BufferReader reader) throws Exception {
		if (id == ChunkTilePacket.PACKET_ID) return ChunkTilePacket.read(reader);
		if (id == ClientboundEncryptionRequestPacket.PACKET_ID) return ClientboundEncryptionRequestPacket.read(reader);
		if (id == ClientboundChunkTimestampsResponsePacket.PACKET_ID) return ClientboundChunkTimestampsResponsePacket.read(reader);
		if (id == ClientboundRegionTimestampsPacket.PACKET_ID) return ClientboundRegionTimestampsPacket.read(reader);
		return null;
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf buf, List<Object> out) {
		try {
			byte id = buf.readByte();
			final Packet packet = constructServerPacket(id, new BufferReader(buf));
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
