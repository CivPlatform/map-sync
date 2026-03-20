package gjum.minecraft.mapsync.mod;

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
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;

/**
 * Stores each chunk's timestamp of when it was received from mc.
 * Persists them grouped by region at `.minecraft/MapSync/cache/{mcServerName}/{dimensionName}/r{x},{z}.chunkmeta`.
 * Each region's LastModifiedTime is set to the oldest contained chunk (or 0 if any chunks are absent), to easily find regions to request from the sync server.
 */
public class DimensionChunkMeta {
	public final String mcServerName;
	public final String dimensionName;
	private final String dimensionDirPath;

	private final HashMap<RegionPos, long[]> regionsTimestamps = new HashMap<>();

	DimensionChunkMeta(String mcServerName, String dimensionName) {
		this.mcServerName = mcServerName;
		this.dimensionName = dimensionName;
		final String mcRoot = Minecraft.getInstance().gameDirectory.getAbsolutePath();
		var dir = Path.of(mcRoot, "MapSync", "cache",
				mcServerName.replaceAll(":", "~"), dimensionName.replaceAll(":", "~"));
		dir.toFile().mkdirs();
		this.dimensionDirPath = dir.toAbsolutePath().toString();
	}

	private Path getRegionFilePath(RegionPos regionPos) {
		return Path.of(dimensionDirPath, "r%d,%d.chunkmeta".formatted(regionPos.x(), regionPos.z()));
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
			Path dir = Path.of(dimensionDirPath);
			if (Files.exists(dir)) {
				Files.walk(dir)
					.sorted((a, b) -> b.compareTo(a)) // delete children first
					.forEach(path -> {
						try { Files.delete(path); }
						catch (IOException e) { e.printStackTrace(); }
					});
			}
			Files.createDirectories(dir);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private long[] readRegionTimestampsFile(RegionPos regionPos) {
		long[] longs = new long[RegionPos.CHUNKS_IN_REGION];
		try {
			final byte[] byteArray = Files.readAllBytes(getRegionFilePath(regionPos));
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
			Path path = getRegionFilePath(regionPos);
			Files.write(path, buffer.array());
			// include absent chunks (ts=0) because sync server may have a chunk there (i.e. newer than 0)
			long oldestChunkTs = Arrays.stream(chunkTimestamps).min().orElseThrow();
			Files.setLastModifiedTime(path, FileTime.fromMillis(oldestChunkTs));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
