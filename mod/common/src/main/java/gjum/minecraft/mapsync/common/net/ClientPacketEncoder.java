package gjum.minecraft.mapsync.common.net;

import gjum.minecraft.mapsync.common.net.packet.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class ClientPacketEncoder extends MessageToByteEncoder<Packet> {
	public static int getClientPacketId(Packet packet) {
		if (packet instanceof ChunkTilePacket) return ChunkTilePacket.PACKET_ID;
		if (packet instanceof CHandshake) return CHandshake.PACKET_ID;
		if (packet instanceof CEncryptionResponse) return CEncryptionResponse.PACKET_ID;
		if (packet instanceof CCatchupRequest) return CCatchupRequest.PACKET_ID;
		throw new IllegalArgumentException("Unknown client packet class " + packet);
	}

	@Override
	protected void encode(ChannelHandlerContext ctx, Packet packet, ByteBuf out) {
		try {
			out.writeByte(getClientPacketId(packet));
			packet.write(out);
		} catch (Throwable err) {
			err.printStackTrace();
		}
	}
}
