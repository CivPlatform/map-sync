package gjum.minecraft.mapsync.mod.net;

import gjum.minecraft.mapsync.mod.MapSyncMod;
import gjum.minecraft.mapsync.mod.config.ServerConfig;
import gjum.minecraft.mapsync.mod.data.GameAddress;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.multiplayer.ServerData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SyncClients implements Iterable<SyncClient> {
	private static final Logger LOGGER = LoggerFactory.getLogger(SyncClients.class);

	public final GameAddress gameAddress;
	public final ServerConfig serverConfig;
	private final Map<String, SyncClient> clients = new ConcurrentHashMap<>();

	public SyncClients(
		final @NotNull GameAddress gameAddress,
		final @NotNull ServerConfig serverConfig
	) {
		this.gameAddress = Objects.requireNonNull(gameAddress);
		this.serverConfig = Objects.requireNonNull(serverConfig);
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
			if (!Objects.equals(this.gameAddress, syncClient.gameAddress)) {
				MapSyncMod.debugLog("Closing client %s as it doesn't match game address %s".formatted(
					syncClient.name(),
					this.gameAddress
				));
				syncClient.websocket.close();
				return null;
			}
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
		syncClient = new SyncClient(syncAddress, this.gameAddress);
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

	// ============================================================
	// Event Hooks
	// ============================================================

	private static volatile SyncClients instance = null;
	private static final VarHandle INSTANCE; static {
		try {
			INSTANCE = MethodHandles.lookup().findStaticVarHandle(SyncClients.class, "instance", SyncClients.class);
		}
		catch (final ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}
	public static Optional<SyncClients> get() {
		return Optional.ofNullable(instance);
	}

	public static void initEvents() {
		ClientPlayConnectionEvents.JOIN.register((gameConnection, sender, minecraft) -> {
			if (INSTANCE.getAndSet((Object) null) instanceof final SyncClients syncClients) {
				syncClients.closeAll(true);
			}
			if (!(gameConnection.getServerData() instanceof final ServerData serverData)) {
				LOGGER.error("Connection doesn't have server data yet... backing out");
				return;
			}
			final GameAddress gameAddress = new GameAddress(serverData.ip);
			final ServerConfig serverConfig;
			try {
				serverConfig = ServerConfig.load(gameAddress);
			}
			catch (final Exception e) {
				LOGGER.error("Could not load server config for {}... backing out", gameAddress, e);
				return;
			}
			final SyncClients syncClients = instance = new SyncClients(
				gameAddress,
				serverConfig
			);
			syncClients.setAll(new HashSet<>(serverConfig.getSyncServerAddresses()));
		});
	}
}
