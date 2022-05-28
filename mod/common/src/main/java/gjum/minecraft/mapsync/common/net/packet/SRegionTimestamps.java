package gjum.minecraft.mapsync.common.net.packet;

import gjum.minecraft.mapsync.common.Utils;
import gjum.minecraft.mapsync.common.data.RegionTimestamp;
import gjum.minecraft.mapsync.common.net.Packet;
import io.netty.buffer.ByteBuf;

public class SRegionTimestamps extends Packet {
  public static final int PACKET_ID = 7;

  private final String dimension;

  private final RegionTimestamp[] timestamps;

  public SRegionTimestamps(String dimension, RegionTimestamp[] timestamps) {
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
    String dimension = Utils.readStringFromBuf(buf);

    short totalRegions = buf.readShort();
    RegionTimestamp[] timestamps = new RegionTimestamp[totalRegions];
    // row = x
    for (short i = 0; i < totalRegions; i++) {
      short regionX = buf.readShort();
      short regionZ = buf.readShort();

      long timestamp = buf.readLong();
      timestamps[i] = new RegionTimestamp(regionX, regionZ, timestamp);
    }

    return new SRegionTimestamps(dimension, timestamps);
  }

  @Override
  public void write(ByteBuf buf) {
    throw new IllegalStateException();
  }
}
