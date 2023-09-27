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

  private final RegionTimestamp[] timestamps;

  public ClientboundRegionTimestampsPacket(String dimension, RegionTimestamp[] timestamps) {
    this.dimension = dimension;
    this.timestamps = timestamps;
  }

  public String getDimension() {
    return dimension;
  }

  public RegionTimestamp[] getTimestamps() {
    return timestamps;
  }

  public static Packet read(ByteBuf buf) {
    String dimension = Packet.readUtf8String(buf);

    short totalRegions = buf.readShort();
    RegionTimestamp[] timestamps = new RegionTimestamp[totalRegions];
    // row = x
    for (short i = 0; i < totalRegions; i++) {
      short regionX = buf.readShort();
      short regionZ = buf.readShort();

      long timestamp = buf.readLong();
      timestamps[i] = new RegionTimestamp(regionX, regionZ, timestamp);
    }

    return new ClientboundRegionTimestampsPacket(dimension, timestamps);
  }
}
