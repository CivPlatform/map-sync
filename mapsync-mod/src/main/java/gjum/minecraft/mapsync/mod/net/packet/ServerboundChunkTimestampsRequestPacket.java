package gjum.minecraft.mapsync.mod.net.packet;

import gjum.minecraft.mapsync.mod.data.RegionPos;
import gjum.minecraft.mapsync.mod.net.IntType;
import gjum.minecraft.mapsync.mod.net.Packet;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

/**
 * You send this in response to a {@link ClientboundRegionTimestampsPacket},
 * listing all the regions you'd like the server to elaborate on. You should
 * expect a {@link ClientboundChunkTimestampsResponsePacket}.
 */
public class ServerboundChunkTimestampsRequestPacket implements Packet {
	public static final int PACKET_ID = 8;

	private final String dimension;
	private final RegionPos region;

	public ServerboundChunkTimestampsRequestPacket(String dimension, RegionPos region) {
		this.dimension = dimension;
		this.region = region;
	}

	@Override
	public void write(@NotNull ByteBuf buf) {
		Packet.writeLengthPrefixedString(buf, IntType.U8, dimension);
		buf.writeShort(region.x());
		buf.writeShort(region.z());
	}
}
