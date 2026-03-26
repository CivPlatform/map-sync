package gjum.minecraft.mapsync.mod.net;

import gjum.minecraft.mapsync.mod.net.buffers.BufferWriter;
import gjum.minecraft.mapsync.mod.net.packet.ChunkTilePacket;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundCatchupRequestPacket;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundChunkTimestampsRequestPacket;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundEncryptionResponsePacket;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundHandshakePacket;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class ServerboundPacketEncoder extends MessageToByteEncoder<Packet> {
	public static int getClientPacketId(Packet packet) {
		if (packet instanceof ChunkTilePacket) return ChunkTilePacket.PACKET_ID;
		if (packet instanceof ServerboundHandshakePacket) return ServerboundHandshakePacket.PACKET_ID;
		if (packet instanceof ServerboundEncryptionResponsePacket) return ServerboundEncryptionResponsePacket.PACKET_ID;
		if (packet instanceof ServerboundCatchupRequestPacket) return ServerboundCatchupRequestPacket.PACKET_ID;
		if (packet instanceof ServerboundChunkTimestampsRequestPacket) return ServerboundChunkTimestampsRequestPacket.PACKET_ID;
		throw new IllegalArgumentException("Unknown client packet class " + packet);
	}

	@Override
	protected void encode(ChannelHandlerContext ctx, Packet packet, ByteBuf out) {
		try {
			out.writeByte(getClientPacketId(packet));
			packet.write(new BufferWriter(out));
		} catch (Throwable err) {
			err.printStackTrace();
			ctx.close();
		}
	}
}
