package gjum.minecraft.mapsync.mod.sync;

import gjum.minecraft.mapsync.mod.MapSyncMod;
import gjum.minecraft.mapsync.mod.net.SyncClient;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

public final class SyncConnections implements Iterable<SyncClient> {
	public final GameContext gameContext;
	private final Map<String, SyncClient> clients;

	SyncConnections(
		final @NotNull GameContext gameContext
	) {
		this.gameContext = Objects.requireNonNull(gameContext);
		this.clients = new ConcurrentHashMap<>();
	}

	@Override
	public @NonNull Iterator<SyncClient> iterator() {
		return this.clients.values().iterator();
	}

	public void setAll(
		final @NotNull Set<@NotNull String> syncAddresses
	) {
		final var syncAddressesCopy = Set.copyOf(syncAddresses);
		this.clients.values().removeIf((syncClient) -> {
			if (syncAddressesCopy.contains(syncClient.syncAddress)) {
				MapSyncMod.debugLog("Closing sync client as %s is not contained within %s".formatted(
					syncClient.syncAddress,
					syncAddressesCopy
				));
				return false;
			}
			syncClient.shouldReconnect = false;
			syncClient.websocket.close();
			return true;
		});
		for (final String syncAddress : syncAddressesCopy) {
			this.clients.compute(syncAddress, this::computeClient);
		}
	}

	private @Nullable SyncClient computeClient(
		final @NotNull String syncAddress,
		SyncClient syncClient
	) {
		if (syncClient != null) {
			switch (syncClient.websocket.getReadyState()) {
				case NOT_YET_CONNECTED:
					syncClient.websocket.connect();
					// fallthrough
				case OPEN:
					return syncClient;
				case CLOSING:
				case CLOSED:
					if (!syncClient.shouldReconnect) {
						return null;
					}
					syncClient.websocket.reconnect();
					return syncClient;
			}
		}
		syncClient = new SyncClient(this.gameContext, syncAddress);
		syncClient.websocket.connect();
		return syncClient;
	}

	public void closeAll(
		final boolean preventReconnect
	) {
		MapSyncMod.debugLog("Closing all sync clients (preventReconnect=%s)".formatted(
			preventReconnect
		));
		this.clients.values().removeIf((syncClient) -> {
			if (preventReconnect) {
				syncClient.shouldReconnect = false;
			}
			syncClient.websocket.close();
			return true;
		});
	}
}
