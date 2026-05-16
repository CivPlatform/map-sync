package gjum.minecraft.mapsync.mod.sync;

import gjum.minecraft.mapsync.mod.data.RegionPos;
import gjum.minecraft.mapsync.mod.integrations.xaerosmap.XaerosWorldMapHelper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// One-shot worker that imports Xaero's per-region cache-file mtimes into
/// MapSync's per-chunk timestamp index for the current dimension. Closes
/// the trust gap on a fresh MapSync install: without it, every chunk Xaero
/// already has data for stays at NULLISH_TIMESTAMP in MapSync, which means
/// the preserve-existing-map-data safeguard skips every catchup update
/// (including legitimate newer ones from friends).
///
/// After this runs, every Xaero-known chunk carries a realistic baseline
/// timestamp (the region's mtime — a coarse but defensible proxy for "last
/// time this area got touched"). From then on the existing newer-wins
/// logic in RenderQueue does the right thing automatically: stale catchup
/// from before that mtime is skipped, fresh updates render.
///
/// Runs once per `(server, dimension)` and tracks completion via a
/// `.xaero-backfilled` marker file next to the chunkmeta. Skips silently
/// when Xaero isn't installed (other map mods retain the basic NULLISH
/// safeguard as a backstop).
public final class XaeroMtimeBackfill {
	private static final Logger logger = LoggerFactory.getLogger(XaeroMtimeBackfill.class);
	private static final String MARKER_FILENAME = ".xaero-backfilled";
	private static final long DETECTION_POLL_INTERVAL_MS = 500L;
	private static final long DETECTION_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(2);

	private XaeroMtimeBackfill() {
	}

	/// Schedules the backfill on a daemon thread. Returns immediately. If
	/// the marker already exists or Xaero isn't installed, the schedule is
	/// a no-op.
	public static void runIfNeeded(
		final @NotNull DimensionChunkMeta meta
	) {
		if (XaerosWorldMapHelper.isXaerosWorldMapNotAvailable) {
			return;
		}
		final Path markerPath = meta.getDimensionDirPath().resolve(MARKER_FILENAME);
		if (Files.exists(markerPath)) {
			return;
		}
		final Thread worker = new Thread(
			() -> runBackfill(meta, markerPath),
			"MapSync-Xaero-Backfill"
		);
		worker.setDaemon(true);
		worker.start();
	}

	private static void runBackfill(
		final @NotNull DimensionChunkMeta meta,
		final @NotNull Path markerPath
	) {
		try {
			if (!waitForXaeroDetection()) {
				logger.warn(
					"Xaero region detection still incomplete after {}ms — skipping mtime backfill for {}",
					DETECTION_TIMEOUT_MS,
					meta.getDimensionDirPath()
				);
				return;
			}
			// Re-check the marker after the wait: a fast dimension swap can
			// schedule two backfills for the same dim before either runs.
			if (Files.exists(markerPath)) {
				return;
			}
			final int[] regionCount = {0};
			XaerosWorldMapHelper.iterateExistingRegions((rx, rz, mtime) -> {
				meta.bulkSetRegionTimestamp(new RegionPos(rx, rz), mtime);
				regionCount[0]++;
			});
			Files.createDirectories(markerPath.getParent());
			Files.writeString(markerPath, Instant.now().toString());
			logger.info(
				"Backfilled {} Xaero region mtimes into MapSync chunkmeta at {}",
				regionCount[0],
				meta.getDimensionDirPath()
			);
		}
		catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		catch (final Exception e) {
			// Don't write the marker — the next session will retry.
			logger.warn("Xaero mtime backfill failed for {}", meta.getDimensionDirPath(), e);
		}
	}

	private static boolean waitForXaeroDetection() throws InterruptedException {
		final long deadline = System.currentTimeMillis() + DETECTION_TIMEOUT_MS;
		while (!XaerosWorldMapHelper.hasDoneRegionDetection()) {
			if (System.currentTimeMillis() >= deadline) {
				return false;
			}
			Thread.sleep(DETECTION_POLL_INTERVAL_MS);
		}
		return true;
	}
}
