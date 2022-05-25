package gjum.minecraft.mapsync.common;

import gjum.minecraft.mapsync.common.data.ChunkTile;
import gjum.minecraft.mapsync.common.integration.JourneyMapHelper;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;

public class RenderQueue {
	// should be how many chunks we can render in the time it takes for a round-trip region chunks request
	public static int WATERMARK_REQUEST_MORE = 0;

	private final DimensionState dimensionState;

	private Thread thread;

	private PriorityBlockingQueue<ChunkTile> queue = new PriorityBlockingQueue<>(18,
			// newest chunks first
			Comparator.comparingLong(ChunkTile::timestamp).reversed());

	public RenderQueue(DimensionState dimensionState) {
		this.dimensionState = dimensionState;
	}

	/**
	 * don't push chunks from mc - they're rendered by the installed map mod
	 */
	public synchronized void renderLater(@NotNull ChunkTile chunkTile) {
		queue.add(chunkTile);
		if (thread == null) {
			thread = new Thread(this::renderLoop);
			thread.start();
		}
	}

	public synchronized void shutDown() {
		if (thread != null) {
			thread.interrupt();
			thread = null;
		}
	}

	private void renderLoop() {
		try {
			while (true) {
				var chunkTile = queue.poll();
				if (chunkTile == null) return;

				if (chunkTile.dimension() != Minecraft.getInstance().level.dimension()) {
					continue; // mod renderers would render this to the wrong dimension
				}

				// chunks from sync server (live, region) will always be older than mc, so mc will take priority
				if (chunkTile.timestamp() < dimensionState.getChunkTimestamp(chunkTile.chunkPos())) {
					continue; // don't overwrite newer data with older data
				}

				boolean renderedJM = JourneyMapHelper.updateWithChunkTile(chunkTile);

				if (renderedJM) {
					dimensionState.setChunkTimestamp(chunkTile.chunkPos(), chunkTile.timestamp());
					dimensionState.writeLastTimestamp(chunkTile.timestamp());
				} // otherwise, update this chunk again when server sends it again

				Thread.sleep(0); // allow stopping via thread.interrupt()

				if (queue.size() < WATERMARK_REQUEST_MORE) {
					// XXX request region from server if not already in-flight
				}
			}
		} catch (InterruptedException ignored) {
			// exit silently
		} catch (Throwable err) {
			err.printStackTrace();
		} finally {
			synchronized (this) {
				thread = null;
			}
		}
	}

	public static boolean areAllMapModsMapping() {
		return JourneyMapHelper.isMapping();
	}
}
