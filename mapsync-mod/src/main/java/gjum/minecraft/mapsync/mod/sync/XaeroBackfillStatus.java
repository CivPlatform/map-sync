package gjum.minecraft.mapsync.mod.sync;

import org.jetbrains.annotations.NotNull;

/// Observable status of the per-DimensionState Xaero mtime backfill. Held
/// on the DimensionState as an AtomicReference so the renderer thread can
/// read it cheaply, and surfaced in SyncConnectionsGui so players can see
/// the import progress (or its absence — e.g. when Xaero isn't installed).
public sealed interface XaeroBackfillStatus
	permits XaeroBackfillStatus.NotNeeded,
	        XaeroBackfillStatus.WaitingForXaero,
	        XaeroBackfillStatus.Backfilling,
	        XaeroBackfillStatus.Completed,
	        XaeroBackfillStatus.Failed {

	/// Xaero isn't installed, or the marker file from a previous session
	/// is already present — nothing to do.
	record NotNeeded(@NotNull String reason) implements XaeroBackfillStatus {
	}

	/// Worker is polling Xaero's region-detection completion flag.
	record WaitingForXaero() implements XaeroBackfillStatus {
	}

	/// Worker is iterating Xaero regions and bulk-seeding chunkmeta. The
	/// count rises in real time so the GUI can show progress.
	record Backfilling(int regionsDone) implements XaeroBackfillStatus {
	}

	/// Worker finished successfully. The marker file is on disk; future
	/// sessions for this (server, dim) won't re-run.
	record Completed(int regionsDone) implements XaeroBackfillStatus {
	}

	/// Worker aborted (timeout, IO error, Xaero internals threw). No marker
	/// written; next session will retry.
	record Failed(@NotNull String reason) implements XaeroBackfillStatus {
	}
}
