package gjum.minecraft.mapsync.mod.server.db;

import gjum.minecraft.mapsync.mod.data.RegionTimestamp;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/// SQLite-backed persistence for the bundled server. Schema is intentionally
/// identical to the standalone TypeScript server's so an old `db.sqlite` can
/// be dropped into the new per-world data directory without migration.
///
/// Two tables:
/// - `chunk_data(hash BLOB PRIMARY KEY, version INTEGER, data BLOB)` —
///   deduplicated chunk payloads keyed by SHA-1.
/// - `player_chunk(world, chunk_x, chunk_z, uuid, ts, hash)` — one row per
///   (player, chunk) pair, pointing at the chunk_data row that player last
///   sent.
public final class MapSyncDatabase implements AutoCloseable {
	private final Connection conn;

	private MapSyncDatabase(
		final @NotNull String jdbcUrl
	) throws SQLException {
		this.conn = DriverManager.getConnection(jdbcUrl);
		this.conn.setAutoCommit(true);
		applySchema();
	}

	public static @NotNull MapSyncDatabase openFile(
		final @NotNull Path dbPath
	) throws SQLException {
		final Path parent = dbPath.getParent();
		if (parent != null) {
			try {
				Files.createDirectories(parent);
			}
			catch (final Exception e) {
				throw new SQLException("Could not prepare data directory " + parent, e);
			}
		}
		return new MapSyncDatabase("jdbc:sqlite:" + dbPath);
	}

	public static @NotNull MapSyncDatabase openInMemory() throws SQLException {
		return new MapSyncDatabase("jdbc:sqlite::memory:");
	}

	private void applySchema() throws SQLException {
		try (final Statement st = this.conn.createStatement()) {
			st.executeUpdate("""
				CREATE TABLE IF NOT EXISTS chunk_data (
					hash BLOB NOT NULL PRIMARY KEY,
					version INTEGER NOT NULL,
					data BLOB NOT NULL
				)
				""");
			st.executeUpdate("""
				CREATE TABLE IF NOT EXISTS player_chunk (
					world TEXT NOT NULL,
					chunk_x INTEGER NOT NULL,
					chunk_z INTEGER NOT NULL,
					uuid TEXT NOT NULL,
					ts BIGINT NOT NULL,
					hash BLOB NOT NULL,
					PRIMARY KEY (world, chunk_x, chunk_z, uuid),
					FOREIGN KEY (hash) REFERENCES chunk_data (hash)
				)
				""");
		}
	}

	/// Region-level catch-up summary: for every region in this dimension,
	/// the newest write timestamp across all players. Used to seed a
	/// freshly-connected client with the broadest possible view.
	public @NotNull List<RegionTimestamp> getRegionTimestamps(
		final @NotNull String dimension
	) throws SQLException {
		final var out = new ArrayList<RegionTimestamp>();
		// SQLite floor() on negative numbers rounds toward zero, not negative
		// infinity, so use integer division via shift-equivalent math instead.
		final String sql = """
			SELECT
				CASE WHEN chunk_x < 0 THEN (chunk_x - 31) / 32 ELSE chunk_x / 32 END AS regionX,
				CASE WHEN chunk_z < 0 THEN (chunk_z - 31) / 32 ELSE chunk_z / 32 END AS regionZ,
				MAX(ts) AS timestamp
			FROM player_chunk
			WHERE world = ?
			GROUP BY regionX, regionZ
			ORDER BY regionX DESC
			""";
		try (final PreparedStatement ps = this.conn.prepareStatement(sql)) {
			ps.setString(1, dimension);
			try (final ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					out.add(new RegionTimestamp(
						(short) rs.getInt("regionX"),
						(short) rs.getInt("regionZ"),
						rs.getLong("timestamp")
					));
				}
			}
		}
		return out;
	}

	/// Chunk-level catch-up summary for one region in one dimension. One row
	/// per (chunk_x, chunk_z), carrying the newest write timestamp across
	/// players.
	public @NotNull List<ChunkTimestamp> getChunkTimestamps(
		final @NotNull String dimension,
		final int regionX,
		final int regionZ
	) throws SQLException {
		final int minChunkX = regionX << 5;
		final int maxChunkX = minChunkX + 32;
		final int minChunkZ = regionZ << 5;
		final int maxChunkZ = minChunkZ + 32;
		final var out = new ArrayList<ChunkTimestamp>();
		final String sql = """
			SELECT chunk_x, chunk_z, MAX(ts) AS timestamp
			FROM player_chunk
			WHERE world = ? AND chunk_x >= ? AND chunk_x < ? AND chunk_z >= ? AND chunk_z < ?
			GROUP BY chunk_x, chunk_z
			ORDER BY ts DESC
			""";
		try (final PreparedStatement ps = this.conn.prepareStatement(sql)) {
			ps.setString(1, dimension);
			ps.setInt(2, minChunkX);
			ps.setInt(3, maxChunkX);
			ps.setInt(4, minChunkZ);
			ps.setInt(5, maxChunkZ);
			try (final ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					out.add(new ChunkTimestamp(
						rs.getInt("chunk_x"),
						rs.getInt("chunk_z"),
						rs.getLong("timestamp")
					));
				}
			}
		}
		return out;
	}

	/// Most-recent payload for a single chunk: joins the newest `player_chunk`
	/// row for this (dim, x, z) against the deduplicated `chunk_data` row.
	public @NotNull Optional<StoredChunk> getChunkData(
		final @NotNull String dimension,
		final int chunkX,
		final int chunkZ
	) throws SQLException {
		final String sql = """
			SELECT chunk_data.hash, chunk_data.version, chunk_data.data, player_chunk.ts
			FROM player_chunk
			JOIN chunk_data ON chunk_data.hash = player_chunk.hash
			WHERE player_chunk.world = ?
			  AND player_chunk.chunk_x = ?
			  AND player_chunk.chunk_z = ?
			ORDER BY player_chunk.ts DESC
			LIMIT 1
			""";
		try (final PreparedStatement ps = this.conn.prepareStatement(sql)) {
			ps.setString(1, dimension);
			ps.setInt(2, chunkX);
			ps.setInt(3, chunkZ);
			try (final ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					return Optional.empty();
				}
				return Optional.of(new StoredChunk(
					rs.getBytes("hash"),
					rs.getInt("version"),
					rs.getLong("ts"),
					rs.getBytes("data")
				));
			}
		}
	}

	/// Records one player's report of a chunk's contents. The actual payload
	/// is deduplicated by hash in `chunk_data`; the `player_chunk` row
	/// associates this (player, chunk) with the payload and a timestamp.
	public void storeChunkData(
		final @NotNull String dimension,
		final int chunkX,
		final int chunkZ,
		final @NotNull UUID playerUuid,
		final long timestamp,
		final int dataVersion,
		final byte @NotNull [] hash,
		final byte @NotNull [] data
	) throws SQLException {
		final boolean previousAutoCommit = this.conn.getAutoCommit();
		this.conn.setAutoCommit(false);
		try {
			try (final PreparedStatement ps = this.conn.prepareStatement(
				"INSERT INTO chunk_data (hash, version, data) VALUES (?, ?, ?) ON CONFLICT(hash) DO NOTHING"
			)) {
				ps.setBytes(1, hash);
				ps.setInt(2, dataVersion);
				ps.setBytes(3, data);
				ps.executeUpdate();
			}
			try (final PreparedStatement ps = this.conn.prepareStatement(
				"REPLACE INTO player_chunk (world, chunk_x, chunk_z, uuid, ts, hash) VALUES (?, ?, ?, ?, ?, ?)"
			)) {
				ps.setString(1, dimension);
				ps.setInt(2, chunkX);
				ps.setInt(3, chunkZ);
				ps.setString(4, playerUuid.toString());
				ps.setLong(5, timestamp);
				ps.setBytes(6, hash);
				ps.executeUpdate();
			}
			this.conn.commit();
		}
		catch (final SQLException e) {
			this.conn.rollback();
			throw e;
		}
		finally {
			this.conn.setAutoCommit(previousAutoCommit);
		}
	}

	@Override
	public void close() throws SQLException {
		this.conn.close();
	}
}
