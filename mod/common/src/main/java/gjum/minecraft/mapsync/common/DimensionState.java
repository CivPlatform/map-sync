package gjum.minecraft.mapsync.common;

import gjum.minecraft.mapsync.common.data.CatchupChunk;
import gjum.minecraft.mapsync.common.data.ChunkTile;
import gjum.minecraft.mapsync.common.integration.JourneyMapHelper;
import gjum.minecraft.mapsync.common.integration.VoxelMapHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.*;

import static gjum.minecraft.mapsync.common.MapSyncMod.debugLog;
import static gjum.minecraft.mapsync.common.MapSyncMod.getMod;

/**
 * contains any background processes and data structures, to be able to easily tear down when leaving the dimension
 */
public class DimensionState {
	private static final Minecraft mc = Minecraft.getInstance();

	public final ResourceKey<Level> dimension;
	boolean hasShutDown = false;

	private final DimensionChunkMeta chunkMeta;
	private final RenderQueue renderQueue;

	private List<CatchupChunk> catchupChunks;

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

	public long readLastTimestamp(){
		return chunkMeta.readLastTimestamp();
	}

	public void writeLastTimestamp(long timestamp){
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

		// voxelmap doesn't need a render queue
		if (!VoxelMapHelper.isMapping() && !JourneyMapHelper.isMapping()) {
			setChunkTimestamp(chunkTile.chunkPos(), chunkTile.timestamp());
		}
	}

	public void setCatchupChunks(List<CatchupChunk> catchupChunks) {
		this.catchupChunks = catchupChunks;
	}

	public void requestCatchupChunks(int amount){
		// calculate Euclidean distance to a given chunk, request closest/newest chunks first.
		PriorityQueue<CatchupChunk> queue = new PriorityQueue<>(amount, (o1, o2) -> {
			Player player = mc.player;
			try {
				return (int) Math.abs(o2.getDistanceTo(player.chunkPosition()) - o1.getDistanceTo(player.chunkPosition()));
			} catch(NullPointerException e) {
				e.printStackTrace();
				return Integer.MAX_VALUE;
			}
		});

		for (CatchupChunk chunk : catchupChunks) {
			var current_timestamp = getChunkTimestamp(chunk.chunkPos());
			if (current_timestamp < chunk.timestamp()) {
				queue.add(chunk);
			} else {
				// no more need, since current timestamp is higher
				catchupChunks.remove(chunk);
			}
		}

		// Dump into list to move
		List<CatchupChunk> chunksToRequest = new ArrayList<>();
		while (!queue.isEmpty()){
			chunksToRequest.add(queue.poll());
		}

		getMod().requestCatchupData(chunksToRequest);

	}

}
