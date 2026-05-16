package gjum.minecraft.mapsync.mod.net.discovery;

import com.google.common.net.HostAndPort;
import gjum.minecraft.mapsync.mod.MapSyncMod;
import gjum.minecraft.mapsync.mod.config.ServerConfig;
import gjum.minecraft.mapsync.mod.sync.GameContext;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/// Client-side receiver for [SyncAddressPayload]. When the server advertises
/// its MapSync endpoint on join, this hands the resolved `ws://host:port`
/// URL to the [GameContext]'s [ServerConfig] and triggers the existing
/// sync-connection machinery — but only when the user has the autoConnect
/// kill-switch enabled. Discovered addresses are persisted to the per-game
/// config so subsequent joins to the same MC server retain a known-good
/// address even if the server later stops advertising.
public final class SyncAddressClientHandler {
	private static final Logger logger = LogManager.getLogger(SyncAddressClientHandler.class);

	private SyncAddressClientHandler() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(SyncAddressPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> apply(payload));
		});
	}

	private static void apply(
		final SyncAddressPayload payload
	) {
		final GameContext gameContext = GameContext.get().orElse(null);
		if (gameContext == null) {
			// We're between INIT and DISCONNECT — payload landed too late to
			// matter for this session. Drop silently rather than crash.
			return;
		}
		final String host = resolveHost(payload, gameContext);
		if (host == null) {
			logger.warn("Server advertised MapSync address but host could not be resolved (payload host='{}')",
				payload.host());
			return;
		}
		if (payload.port() <= 0 || payload.port() > 65535) {
			logger.warn("Server advertised MapSync address with invalid port {}", payload.port());
			return;
		}
		final String url = "ws://" + host + ":" + payload.port();
		final ServerConfig gameConfig = gameContext.getGameConfig();
		final List<String> next = List.of(url);
		final List<String> current = gameConfig.getSyncServerAddresses();
		if (!current.equals(next)) {
			gameConfig.setSyncServerAddresses(next);
			gameConfig.save();
			logger.info("Discovered MapSync address {} (was {})", url, current);
		}
		if (!gameConfig.shouldAutoConnect()) {
			return;
		}
		gameContext.getSyncConnections().setAll(Set.of(url));
		MapSyncMod.debugLog("Auto-connecting to discovered MapSync " + url);
	}

	/// Picks the host the client should connect to. Payload host wins when
	/// non-blank (operator override for proxy setups); otherwise falls back to
	/// whatever host the client used to reach the MC server. Returns null if
	/// both are unusable.
	private static String resolveHost(
		final SyncAddressPayload payload,
		final GameContext gameContext
	) {
		final String fromPayload = payload.host().trim();
		if (!fromPayload.isEmpty()) {
			return fromPayload;
		}
		try {
			return HostAndPort.fromString(gameContext.getGameAddress().address()).getHost();
		}
		catch (final Exception e) {
			return null;
		}
	}
}
