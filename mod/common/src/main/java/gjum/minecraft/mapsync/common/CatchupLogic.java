package gjum.minecraft.mapsync.common;

import gjum.minecraft.mapsync.common.data.CatchupChunk;
import gjum.minecraft.mapsync.common.data.ChunkTile;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static gjum.minecraft.mapsync.common.MapSyncMod.*;

public class CatchupLogic {
	int nearbyDistance = 20; // chunk distance from player to prioritize in requests

	private final DimensionState dimensionState;

	/**
	 * sorted by newest to oldest; nearby chunks may come first and be sorted by distance to player
	 */
	private final List<CatchupChunk> catchupChunks = new ArrayList<>();

	private final long beginLiveTs = System.currentTimeMillis();
	private long tsRequestMore = 0;

	public CatchupLogic(DimensionState dimensionState) {
		this.dimensionState = dimensionState;
	}

	public void addCatchupChunks(List<CatchupChunk> catchupChunks) {
		if (catchupChunks.isEmpty()) return;
		var catchupDim = catchupChunks.get(0).dimension();
		if (!dimensionState.dimension.equals(catchupDim)) {
			logger.warn("Catchup chunks from wrong dimension " + catchupDim + ", expected " + dimensionState.dimension);
			return;
		}
		synchronized (this.catchupChunks) {
			for (CatchupChunk chunk : catchupChunks) {
				// only include catchup chunks that are newer than the corresponding chunk we have locally
				var current_timestamp = dimensionState.getChunkTimestamp(chunk.chunkPos());
				if (current_timestamp < chunk.timestamp()) {
					this.catchupChunks.add(chunk);
				}
			}
			debugLog("now have " + this.catchupChunks.size() + " catchup chunks");
		}
		maybeRequestMoreCatchup();
	}

	public void handleSharedChunkReceived(@NotNull ChunkTile chunkTile) {
		if (chunkTile.timestamp() < beginLiveTs) {
			// assume received chunk is catchup chunk
			// wait a bit to allow more catchup chunks to come in before requesting more
			tsRequestMore = System.currentTimeMillis() + 100;
		}
	}

	void maybeRequestMoreCatchup() {
		int queueSize = dimensionState.getRenderQueueSize();
		int WATERMARK_REQUEST_MORE = MapSyncMod.modConfig.getCatchupWatermark();
		long now = System.currentTimeMillis();
		if (queueSize < WATERMARK_REQUEST_MORE && tsRequestMore < now) {
			// before requesting more, wait for a catchup chunk to be received (see renderLater());
			// if none get received within a second (all outdated etc.) then request more anyway
			tsRequestMore = now + 5000;
			var chunksToRequest = pollCatchupChunks(WATERMARK_REQUEST_MORE);
			getMod().requestCatchupData(chunksToRequest);
		}
	}

	/**
	 * Removes and returns up to `amount` chunks from `catchupChunks`.
	 * Prioritizes nearby chunks (up to 32 chunks euclidean from player), then newest chunks.
	 */
	private @NotNull List<CatchupChunk> pollCatchupChunks(int amount) {
		Player player = Minecraft.getInstance().player;
		if (player == null) return Collections.emptyList();
		ChunkPos playerPos = player.chunkPosition();

		synchronized (catchupChunks) {
			if (catchupChunks.isEmpty()) return Collections.emptyList();
			// remove outdated chunks,
			// prioritise nearby chunks
			// by filtering into two lists and dropping outdated chunks, we can just use the two lists below to replace catchupChunks
			final List<CatchupChunk> nearbyChunks = new ArrayList<>();
			final List<CatchupChunk> farChunks = new ArrayList<>();
			int nearbySq = nearbyDistance * nearbyDistance;
			for (CatchupChunk chunk : catchupChunks) {
				var current_timestamp = dimensionState.getChunkTimestamp(chunk.chunkPos());
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
