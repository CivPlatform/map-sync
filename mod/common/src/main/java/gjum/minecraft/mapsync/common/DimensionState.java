package gjum.minecraft.mapsync.common;

import gjum.minecraft.mapsync.common.data.ChunkTile;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import static gjum.minecraft.mapsync.common.MapSyncMod.debugLog;

/**
 * contains any background processes and data structures, to be able to easily tear down when leaving the dimension
 */
public class DimensionState {
	private static final Minecraft mc = Minecraft.getInstance();

	public final ResourceKey<Level> dimension;
	boolean hasShutDown = false;

	private final DimensionChunkMeta chunkMeta;
	private final RenderQueue renderQueue;

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
	}
}
