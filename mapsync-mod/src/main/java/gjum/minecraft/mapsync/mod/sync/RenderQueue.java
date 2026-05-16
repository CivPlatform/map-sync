package gjum.minecraft.mapsync.mod.sync;

import static gjum.minecraft.mapsync.mod.MapSyncMod.debugLog;

import gjum.minecraft.mapsync.mod.data.ChunkTile;
import gjum.minecraft.mapsync.mod.integrations.journeymap.JourneyMapHelper;
import gjum.minecraft.mapsync.mod.integrations.voxelmap.VoxelMapHelper;
import gjum.minecraft.mapsync.mod.integrations.xaerosmap.XaerosWorldMapHelper;
import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.NotNull;

public class RenderQueue {
	private final DimensionState dimensionState;

	private Thread thread;

	private final PriorityBlockingQueue<ChunkTile> queue = new PriorityBlockingQueue<>(18,
			// newest chunks first
			Comparator.comparingLong(ChunkTile::timestamp).reversed());

	public RenderQueue(DimensionState dimensionState) {
		this.dimensionState = dimensionState;
	}

	public int getQueueSize() {
		return queue.size();
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
				Thread.sleep(0); // allow stopping via thread.interrupt()

				if (Minecraft.getInstance().level == null) {
					return; // world closed; all queued chunks can't be rendered
				}

				if (!JourneyMapHelper.isJourneyMapNotAvailable && !JourneyMapHelper.isMapping()
						|| VoxelMapHelper.isModAvailable && !VoxelMapHelper.isMapping()
						|| !XaerosWorldMapHelper.isXaerosWorldMapNotAvailable && !XaerosWorldMapHelper.isMapping()
				) {
					debugLog("render is waiting til map mod is ready");
					Thread.sleep(1000);
					continue;
				}

				var chunkTile = queue.poll();
				if (chunkTile == null) return;

				if (chunkTile.dimension() != Minecraft.getInstance().level.dimension()) {
					debugLog("skipping render wrong dim " + chunkTile.chunkPos());
					continue; // mod renderers would render this to the wrong dimension
				}

				// chunks from sync server (live, region) will always be older than mc, so mc will take priority
				final long existingTs = dimensionState.getChunkTimestamp(chunkTile.chunkPos());
				if (chunkTile.timestamp() < existingTs) {
					// don't overwrite newer data with older data
					debugLog("skipping render outdated " + chunkTile.chunkPos());
				} else if (existingTs == DimensionChunkMeta.NULLISH_TIMESTAMP
						&& shouldPreserveExistingMapData()
						&& anyMapModHasChunk(chunkTile.chunkPos())) {
					// MapSync has never seen this chunk for this server AND at
					// least one installed map mod already has data we shouldn't
					// overwrite. Skip the render AND deliberately do not record
					// a timestamp — future updates for the same chunk will land
					// in this branch again until the player physically loads
					// the chunk in-game (which sets a real timestamp). Chunks
					// that no map mod has data for fall through and render
					// normally — that's the "create where missing" path.
					debugLog("skipping render to preserve existing map data " + chunkTile.chunkPos());
				} else {
					boolean voxelRendered = VoxelMapHelper.updateWithChunkTile(chunkTile);
					boolean renderedJM = JourneyMapHelper.updateWithChunkTile(chunkTile);
					boolean xaeroRendered = XaerosWorldMapHelper.updateWithChunkTile(chunkTile);

					debugLog("rendered? " + (voxelRendered||renderedJM|| xaeroRendered) + " " + chunkTile.chunkPos() + " queue=" + queue.size());

					if (renderedJM || voxelRendered || xaeroRendered) {
						dimensionState.setChunkTimestamp(chunkTile.chunkPos(), chunkTile.timestamp());
					} // otherwise, update this chunk again when server sends it again
				}

				// count skipped(outdated) chunks too so DimensionState's "received" vs "rendered" count matches up
				dimensionState.onChunkRenderDone(chunkTile);
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

	/// Fail-safe accessor for the safeguard flag. Defaults to true (preserve)
	/// when the GameContext is unavailable — we'd rather drop a chunk we
	/// could have rendered than risk overwriting local data the player
	/// trusted.
	private static boolean shouldPreserveExistingMapData() {
		return GameContext.get()
			.map((ctx) -> ctx.getGameConfig().shouldPreserveExistingMapData())
			.orElse(true);
	}

	/// True if any installed map mod reports existing data for this chunk.
	/// Each helper returns false when its mod isn't loaded (nothing to
	/// protect) — so this only ORs across mods the player actually has.
	private static boolean anyMapModHasChunk(final ChunkPos pos) {
		return XaerosWorldMapHelper.hasExistingChunkData(pos)
			|| JourneyMapHelper.hasExistingChunkData(pos)
			|| VoxelMapHelper.hasExistingChunkData(pos);
	}
}
