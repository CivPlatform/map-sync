package gjum.minecraft.mapsync.common.net;

import gjum.minecraft.mapsync.common.net.packet.*;
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
			packet.write(out);
		} catch (Throwable err) {
			err.printStackTrace();
			ctx.close();
		}
	}
}
