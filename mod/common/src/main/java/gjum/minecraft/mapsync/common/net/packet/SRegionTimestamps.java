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
    short count = 0;
    // row = x
    while (totalRegions > 0) {
      short regionX = buf.readShort();
      short regionZ = buf.readShort();
      short regionColumns = buf.readShort();
      totalRegions -= regionColumns;
      if (regionColumns == 0) {
        throw new IllegalStateException("Region columns 0 at (" + regionX + " " + regionZ + ")");
      }
      if (totalRegions < 0) {
        throw new IllegalStateException("Malformed data from server! Total regions " + totalRegions + " from region row (" + regionX + ", " + regionZ + ") with " + regionColumns + " regions");
      }

      for (short column = 0; column < regionColumns; column++) {
        short realRegionX = (short) (regionX + column);

        long timestamp = buf.readLong();
        timestamps[count++] = new RegionTimestamp(realRegionX, regionZ, timestamp);
      }
    }

    return new SRegionTimestamps(dimension, timestamps);
  }

  @Override
  public void write(ByteBuf buf) {
    throw new IllegalStateException();
  }
}
