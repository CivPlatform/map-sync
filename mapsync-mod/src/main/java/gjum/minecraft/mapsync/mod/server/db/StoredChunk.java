package gjum.minecraft.mapsync.mod.server.db;

import org.jetbrains.annotations.NotNull;

/// A chunk row reconstructed from the join across `player_chunk` and
/// `chunk_data` tables: the newest write seen for a given (dimension, chunk).
public record StoredChunk(
	byte @NotNull [] hash,
	int dataVersion,
	long timestamp,
	byte @NotNull [] data
) {
}
