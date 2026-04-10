package gjum.minecraft.mapsync.mod.sync;

import gjum.minecraft.mapsync.mod.data.GameAddress;
import gjum.minecraft.mapsync.mod.data.RegionPos;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.HashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.NotNull;

/**
 * Stores each chunk's timestamp of when it was received from mc.
 * Persists them grouped by region at `.minecraft/MapSync/cache/{mcServerName}/{dimensionName}/r{x},{z}.chunkmeta`.
 * Each region's LastModifiedTime is set to the oldest contained chunk (or 0 if any chunks are absent), to easily find regions to request from the sync server.
 */
public class DimensionChunkMeta {
	public final GameAddress gameAddress;
	public final String dimensionName;
	private final Path dimensionDirPath;

	private final HashMap<RegionPos, long[]> regionsTimestamps = new HashMap<>();

	DimensionChunkMeta(GameAddress gameAddress, String dimensionName) {
		this.gameAddress = gameAddress;
		this.dimensionName = dimensionName;
		this.dimensionDirPath = FabricLoader.getInstance()
			.getGameDir()
			.resolve("data")
			.resolve("MapSync")
			.resolve(gameAddress.asFsName())
			.resolve(dimensionName.replace(":", "~"));
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

	// Only call this to clear memory and file-cache
	public synchronized void PurgeRegionTimeStamps() {
		regionsTimestamps.clear();
		try {
			if (Files.exists(this.dimensionDirPath)) {
				Files.walk(this.dimensionDirPath)
					.sorted((a, b) -> b.compareTo(a)) // delete children first
					.forEach(path -> {
						try { Files.delete(path); }
						catch (IOException e) { e.printStackTrace(); }
					});
			}
			Files.createDirectories(this.dimensionDirPath);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private long[] readRegionTimestampsFile(RegionPos regionPos) {
		long[] longs = new long[RegionPos.CHUNKS_IN_REGION];
		try {
			final byte[] byteArray = Files.readAllBytes(this.dimensionDirPath.resolve(this.getRegionFileName(regionPos)));
			ByteBuffer.wrap(byteArray).asLongBuffer().get(longs);
		} catch (FileNotFoundException | NoSuchFileException ignored) {
		} catch (IOException e) {
			e.printStackTrace();
		}
		return longs;
	}

	private synchronized void writeRegionTimestampsFile(RegionPos regionPos, long[] chunkTimestamps) {
		try {
			final var buffer = ByteBuffer.allocate(8 * RegionPos.CHUNKS_IN_REGION);
			buffer.asLongBuffer().put(chunkTimestamps);
			buffer.flip();
			Files.createDirectories(this.dimensionDirPath);
			Path path = this.dimensionDirPath.resolve(this.getRegionFileName(regionPos));
			Files.write(path, buffer.array());
			// include absent chunks (ts=0) because sync server may have a chunk there (i.e. newer than 0)
			long oldestChunkTs = Arrays.stream(chunkTimestamps).min().orElseThrow();
			Files.setLastModifiedTime(path, FileTime.fromMillis(oldestChunkTs));
		} catch (IOException e) {
			e.printStackTrace();
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
