package gjum.minecraft.mapsync.mod.server.db;

/// Per-chunk newest-write summary within a single region. Returned in batches
/// from {@link MapSyncDatabase#getChunkTimestamps(String, int, int)} and
/// translated to {@code CatchupChunk} entries on the wire.
public record ChunkTimestamp(int chunkX, int chunkZ, long timestamp) {
}
