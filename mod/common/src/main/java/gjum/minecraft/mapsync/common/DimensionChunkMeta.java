package gjum.minecraft.mapsync.common;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.HashMap;

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
		var dir = Path.of(mcRoot, "MapSync", "cache", mcServerName, dimensionName);
		dir.toFile().mkdirs();
		this.dimensionDirPath = dir.toAbsolutePath().toString();
	}

	private Path getRegionFilePath(RegionPos regionPos) {
		return Path.of(dimensionDirPath, "r%d,%d.chunkmeta".formatted(regionPos.x, regionPos.z));
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

	private long[] readRegionTimestampsFile(RegionPos regionPos) {
		long[] longs = new long[RegionPos.CHUNKS_IN_REGION];
		try {
			final byte[] byteArray = Files.readAllBytes(getRegionFilePath(regionPos));
			// from https://stackoverflow.com/questions/3823807/fastest-way-to-read-long-from-file/3823894#3823894
			for (int i = 0; i < RegionPos.CHUNKS_IN_REGION; i += 8) {
				longs[i >> 3] = ((long) byteArray[i] << 56) +
						((long) (byteArray[1 + i] & 255) << 48) +
						((long) (byteArray[2 + i] & 255) << 40) +
						((long) (byteArray[3 + i] & 255) << 32) +
						((long) (byteArray[4 + i] & 255) << 24) +
						((byteArray[5 + i] & 255) << 16) +
						((byteArray[6 + i] & 255) << 8) +
						((byteArray[7 + i] & 255));
			}
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

	/**
	 * Reuse hash etc. Note that most helper methods are useless because of the different physical scale.
	 */
	private static class RegionPos extends ChunkPos {
		static final int CHUNKS_IN_REGION = 32 * 32;

		public RegionPos(int x, int z) {
			super(x, z);
		}

		public static RegionPos forChunkPos(ChunkPos pos) {
			return new RegionPos(pos.x >> 5, pos.z >> 5);
		}

		public static int chunkIndex(ChunkPos pos) {
			return (pos.x & 0b11111) + 32 * (pos.z & 0b11111);
		}
	}
}
