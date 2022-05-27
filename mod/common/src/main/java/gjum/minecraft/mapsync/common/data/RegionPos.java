package gjum.minecraft.mapsync.common.data;

import net.minecraft.world.level.ChunkPos;

public record RegionPos(int x, int z) {

  public static final int CHUNKS_IN_REGION = 32 * 32;

  public static RegionPos forChunkPos(ChunkPos pos) {
    return new RegionPos(pos.x >> 5, pos.z >> 5);
  }

  public static int chunkIndex(ChunkPos pos) {
    return (pos.x & 0b11111) + 32 * (pos.z & 0b11111);
  }

}