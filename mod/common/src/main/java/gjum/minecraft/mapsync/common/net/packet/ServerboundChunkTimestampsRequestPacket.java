package gjum.minecraft.mapsync.common.net.packet;

import gjum.minecraft.mapsync.common.data.RegionPos;
import gjum.minecraft.mapsync.common.net.Packet;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * You send this in response to a {@link ClientboundRegionTimestampsPacket},
 * listing all the regions you'd like the server to elaborate on. You should
 * expect a {@link ClientboundChunkTimestampsResponsePacket}.
 */
public class ServerboundChunkTimestampsRequestPacket implements Packet {
  public static final int PACKET_ID = 8;

  private final String dimension;
  private final List<RegionPos> regions;

  public ServerboundChunkTimestampsRequestPacket(String dimension, List<RegionPos> regions) {
    this.dimension = dimension;
    this.regions = regions;
  }

  @Override
  public void write(@NotNull ByteBuf buf) {
    Packet.writeUtf8String(buf, dimension);
    buf.writeShort(regions.size());
    for (var region : regions) {
      buf.writeShort(region.x());
      buf.writeShort(region.z());
    }
  }
}
