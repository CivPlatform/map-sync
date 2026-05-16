package gjum.minecraft.mapsync.mod.sync;

import gjum.minecraft.mapsync.mod.data.GameAddress;
import gjum.minecraft.mapsync.mod.data.RegionPos;
import gjum.minecraft.mapsync.mod.utils.Assertions;
import gjum.minecraft.mapsync.mod.utils.MagicValues;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores each chunk's timestamp of when it was received from mc.
 * Persists them grouped by region at `.minecraft/MapSync/cache/{mcServerName}/{dimensionName}/r{x},{z}.chunkmeta`.
 * Each region's LastModifiedTime is set to the oldest contained chunk (or 0 if any chunks are absent), to easily find regions to request from the sync server.
 */
public class DimensionChunkMeta {
	private static final Logger LOGGER = LoggerFactory.getLogger(DimensionChunkMeta.class);
	public static final long NULLISH_TIMESTAMP = Long.MIN_VALUE;

	public final GameAddress gameAddress;
	private final Path dimensionDirPath;
	private final Map<RegionPos, long[]> regionsTimestamps;

	DimensionChunkMeta(
		final @NotNull GameAddress gameAddress,
		final @NotNull Identifier dimension
	) {
		this.gameAddress = gameAddress;
		this.dimensionDirPath = FabricLoader.getInstance()
			.getGameDir()
			.resolve("data")
			.resolve("MapSync")
			.resolve(gameAddress.asFsName())
			.resolve(dimension.toString().replace(":", "~"));
		this.regionsTimestamps = new ConcurrentHashMap<>();
	}

	public synchronized long getOldestChunkTsInRegion(RegionPos regionPos) {
		long[] chunkTimestamps = regionsTimestamps.computeIfAbsent(regionPos, this::readRegionTimestampsFile);
		return Arrays.stream(chunkTimestamps).min().orElse(0);
	}

	public synchronized long getTimestamp(ChunkPos chunkPos) {
		final var regionPos = RegionPos.forChunkPos(chunkPos);
		final long[] regionTimestamps = regionsTimestamps.computeIfAbsent(regionPos, this::readRegionTimestampsFile);
		final int chunkNr = RegionPos.chunkIndex(chunkPos);
		return regionTimestamps[chunkNr];
	}

	public synchronized void setTimestamp(ChunkPos chunkPos, long timestamp) {
		final var regionPos = RegionPos.forChunkPos(chunkPos);
		final long[] regionTimestamps = regionsTimestamps.computeIfAbsent(regionPos, this::readRegionTimestampsFile);
		final int chunkNr = RegionPos.chunkIndex(chunkPos);
		regionTimestamps[chunkNr] = timestamp;
		writeRegionTimestampsFile(regionPos, regionTimestamps);
	}

	/// Fills every chunk slot in this region with the same timestamp and
	/// writes the region file once. Used by the one-shot Xaero-mtime
	/// backfill — calling setTimestamp 1024 times per region would rewrite
	/// the file 1024 times, while a player's full Xaero cache can run into
	/// thousands of regions.
	///
	/// Existing per-chunk timestamps for NULLISH slots are overwritten with
	/// the new baseline; non-NULLISH slots are preserved so a chunk already
	/// touched by the live MapSync session (real explore time) wins over
	/// the file-mtime baseline.
	public synchronized void bulkSetRegionTimestamp(
		final @NotNull RegionPos regionPos,
		final long timestamp
	) {
		final long[] regionTimestamps = regionsTimestamps.computeIfAbsent(regionPos, this::readRegionTimestampsFile);
		boolean changed = false;
		for (int i = 0; i < regionTimestamps.length; i++) {
			if (regionTimestamps[i] == NULLISH_TIMESTAMP) {
				regionTimestamps[i] = timestamp;
				changed = true;
			}
		}
		if (changed) {
			writeRegionTimestampsFile(regionPos, regionTimestamps);
		}
	}

	/// Per-dimension data directory. Exposed so the backfill runner can drop
	/// its `.xaero-backfilled` marker next to the chunkmeta files without
	/// having to recompute the path scheme.
	public @NotNull Path getDimensionDirPath() {
		return this.dimensionDirPath;
	}

	// Only call this to clear memory and file-cache
	public synchronized void purgeRegionTimestamps() {
		this.regionsTimestamps.clear();
		try {
			FileUtils.deleteDirectory(this.dimensionDirPath.toFile());
		}
		catch (final IOException e) {
			LOGGER.warn("Failed to purge region timestamps!", e);
		}
	}

	private long @NotNull [] readRegionTimestampsFile(
		final @NotNull RegionPos regionPos
	) {
		final var timestamps = new long[RegionPos.CHUNKS_IN_REGION];
		Arrays.fill(timestamps, NULLISH_TIMESTAMP);
		try {
			final byte[] bytes = Files.readAllBytes(this.dimensionDirPath.resolve(getRegionFileName(regionPos)));
			Assertions.assertLength(bytes.length, Long.BYTES * timestamps.length);
			ByteBuffer.wrap(bytes).asLongBuffer().get(timestamps);
		}
		catch (final FileNotFoundException | NoSuchFileException ignored) {}
		catch (final Exception e) {
			LOGGER.warn("Failed to read region timestamps file for {}", regionPos, e);
		}
		return timestamps;
	}

	private synchronized void writeRegionTimestampsFile(
		final @NotNull RegionPos regionPos,
		final long @NotNull [] timestamps
	) {
		Assertions.assertLength(timestamps.length, MagicValues.REGION_GRID);
		final var bytes = new byte[Long.BYTES * timestamps.length];
		ByteBuffer.wrap(bytes).asLongBuffer().put(timestamps);
		try {
			Files.createDirectories(this.dimensionDirPath);
			Files.write(this.dimensionDirPath.resolve(getRegionFileName(regionPos)), bytes);
		}
		catch (final IOException e) {
			LOGGER.warn("Failed to write region timestamps file for {}", regionPos, e);
		}
	}

	private @NotNull String getRegionFileName(
		final @NotNull RegionPos regionPos
	) {
		return "r%d,%d.chunkmeta".formatted(
			regionPos.x(),
			regionPos.z()
		);
	}
}
