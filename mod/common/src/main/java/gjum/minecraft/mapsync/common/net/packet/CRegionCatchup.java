package gjum.minecraft.mapsync.common.net.packet;

import gjum.minecraft.mapsync.common.Utils;
import gjum.minecraft.mapsync.common.net.Packet;
import io.netty.buffer.ByteBuf;

public class CRegionCatchup extends Packet {
  public static final int PACKET_ID = 8;

  private final String dimension;
  private final short[] regions;

  public CRegionCatchup(String dimension, short[] regions) {
    this.dimension = dimension;
    if (regions.length % 2 != 0) {
      throw new IllegalStateException("Regions length " + regions.length);
    }
    this.regions = regions;
  }

  @Override
  public void write(ByteBuf buf) {
    Utils.writeStringToBuf(buf, dimension);
    buf.writeShort(regions.length / 2);
    for (short s : regions) {
      buf.writeShort(s);
    }
  }
}
