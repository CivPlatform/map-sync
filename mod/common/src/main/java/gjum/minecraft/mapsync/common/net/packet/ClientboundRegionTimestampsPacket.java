package gjum.minecraft.mapsync.common.net.packet;

import gjum.minecraft.mapsync.common.data.RegionTimestamp;
import gjum.minecraft.mapsync.common.net.Packet;
import io.netty.buffer.ByteBuf;

/**
 * This is the packet for the first-stage of the synchronisation process. It's
 * sent immediately after you've been authenticated. You should respond with a
 * {@link ServerboundChunkTimestampsRequestPacket}.
 */
public class ClientboundRegionTimestampsPacket implements Packet {
  public static final int PACKET_ID = 7;

  private final String dimension;

  private final RegionTimestamp timestamp;

  public ClientboundRegionTimestampsPacket(String dimension, RegionTimestamp timestamp) {
    this.dimension = dimension;
    this.timestamp = timestamp;
  }

  public String getDimension() {
    return dimension;
  }

  public RegionTimestamp getTimestamp() {
    return timestamp;
  }

  public static Packet read(ByteBuf buf) {
    return new ClientboundRegionTimestampsPacket(
      Packet.readUtf8String(buf),
      new RegionTimestamp(
        buf.readShort(),
        buf.readShort(),
        buf.readLong()
      )
    );
  }
}
