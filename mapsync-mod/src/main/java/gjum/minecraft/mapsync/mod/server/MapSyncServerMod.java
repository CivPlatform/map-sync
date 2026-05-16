package gjum.minecraft.mapsync.mod.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.ApiStatus;

// Server-side (dedicated server) entrypoint. Today it does nothing beyond
// logging — Phase 2 will plug the websocket server, persistence, and auth
// directly into the Minecraft server lifecycle.
public final class MapSyncServerMod {
	public static final Logger logger = LogManager.getLogger(MapSyncServerMod.class);

	@ApiStatus.Internal
	public static void bootstrap() {
		ServerLifecycleEvents.SERVER_STARTING.register((server) -> {
			logger.info("MapSync server-side initialized (no-op stub — Phase 0)");
		});
	}
}
