package gjum.minecraft.mapsync.common;

import gjum.minecraft.mapsync.common.data.CatchupChunk;
import gjum.minecraft.mapsync.common.data.ChunkTile;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.*;

import static gjum.minecraft.mapsync.common.MapSyncMod.debugLog;
import static gjum.minecraft.mapsync.common.MapSyncMod.logger;

/**
 * contains any background processes and data structures, to be able to easily tear down when leaving the dimension
 */
public class DimensionState {
	private static final Minecraft mc = Minecraft.getInstance();

	public final ResourceKey<Level> dimension;
	boolean hasShutDown = false;

	private final DimensionChunkMeta chunkMeta;
	private final RenderQueue renderQueue;

	/**
	 * sorted by newest to oldest; nearby chunks may come first and be sorted by distance to player
	 */
	private final List<CatchupChunk> catchupChunks = new ArrayList<>();

	DimensionState(String mcServerName, ResourceKey<Level> dimension) {
		this.dimension = dimension;
		String dimensionName = dimension.location().toString();
		chunkMeta = new DimensionChunkMeta(mcServerName, dimensionName);
		renderQueue = new RenderQueue(this);
	}

	public long getChunkTimestamp(ChunkPos chunkPos) {
		return chunkMeta.getTimestamp(chunkPos);
	}

	public void setChunkTimestamp(ChunkPos chunkPos, long timestamp) {
		chunkMeta.setTimestamp(chunkPos, timestamp);
	}

	public long readLastTimestamp() {
		return chunkMeta.readLastTimestamp();
	}

	public void writeLastTimestamp(long timestamp) {
		chunkMeta.writeLastTimestamp(timestamp);
	}

	public synchronized void shutDown() {
		if (hasShutDown) return;
		hasShutDown = true;
		renderQueue.shutDown();
	}

	public void processSharedChunk(ChunkTile chunkTile) {
		if (mc.level == null) return;
		if (dimension != mc.level.dimension()) {
			debugLog("Dropping chunk tile: mc changed dimension");
			shutDown(); // player entered different dimension
			return;
		}

		if (chunkTile.dimension() != dimension) {
			debugLog("Dropping chunk tile: wrong dimension "
					+ chunkTile.dimension() + " wanted " + dimension);
			return; // don't render tile to the wrong dimension
		}

		if (mc.level.getChunkSource().hasChunk(chunkTile.x(), chunkTile.z())) {
			debugLog("Dropping chunk tile: loaded in world");
			return; // don't update loaded chunks
		}

		renderQueue.renderLater(chunkTile);
	}

	public void setCatchupChunks(List<CatchupChunk> catchupChunks) {
		if (catchupChunks.isEmpty()) return;
		var catchupDim = catchupChunks.get(0).dimension();
		if (!dimension.equals(catchupDim)) {
			logger.warn("Catchup chunks from wrong dimension " + catchupDim + ", expected " + dimension);
			return;
		}
		synchronized (this.catchupChunks) {
			for (CatchupChunk chunk : catchupChunks) {
				// only include catchup chunks that are newer than the corresponding chunk we have locally
				var current_timestamp = getChunkTimestamp(chunk.chunkPos());
				if (current_timestamp < chunk.timestamp()) {
					this.catchupChunks.add(chunk);
				}
			}
		}
	}

	/**
	 * Removes and returns up to `amount` chunks from `catchupChunks`.
	 * Prioritizes nearby chunks (up to 32 chunks euclidean from player), then newest chunks.
	 */
	public List<CatchupChunk> pollCatchupChunks(int amount) {
		Player player = mc.player;
		if (player == null) return null;
		ChunkPos playerPos = mc.player.chunkPosition();

		synchronized (catchupChunks) {
			// remove outdated chunks,
			// prioritise nearby chunks
			// by filtering into two lists and dropping outdated chunks, we can just use the two lists below to replace catchupChunks
			final List<CatchupChunk> nearbyChunks = new ArrayList<>();
			final List<CatchupChunk> farChunks = new ArrayList<>();
			int nearbySq = 32 * 32;
			for (CatchupChunk chunk : catchupChunks) {
				var current_timestamp = getChunkTimestamp(chunk.chunkPos());
				if (chunk.timestamp() < current_timestamp) continue; // outdated

				int dsq = chunk.getDistanceSq(playerPos);
				if (dsq < nearbySq) nearbyChunks.add(chunk);
				else farChunks.add(chunk);
			}
			// request nearest first, furthest last
			nearbyChunks.sort(Comparator.comparing((CatchupChunk c) ->
					c.getDistanceSq(playerPos)));
			// there may be too many chunks nearby; limit to amount
			if (nearbyChunks.size() > amount) {
				List<CatchupChunk> chunksToRequest = nearbyChunks.subList(0, amount);
				// all leftovers go back into catchupChunks for future requests
				// NOTE: nearbyChunks is sorted by distance to player, not timestamp; but that's fine because those will probably be in our next request anyway
				catchupChunks.clear();
				catchupChunks.addAll(nearbyChunks.subList(amount, nearbyChunks.size()));
				catchupChunks.addAll(farChunks);
				return chunksToRequest;
			}
			// request nearbyChunks, then add remaining from farChunks (still sorted new to old)
			final List<CatchupChunk> chunksToRequest = nearbyChunks;
			amount -= nearbyChunks.size();
			chunksToRequest.addAll(farChunks.subList(0, Math.min(amount, farChunks.size())));
			// all leftovers go back into catchupChunks for future requests
			catchupChunks.clear();
			if (amount < farChunks.size()) catchupChunks.addAll(farChunks.subList(amount, farChunks.size()));
			return chunksToRequest;
		}
	}
}
