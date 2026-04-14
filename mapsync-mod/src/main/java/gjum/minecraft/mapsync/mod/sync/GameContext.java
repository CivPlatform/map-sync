package gjum.minecraft.mapsync.mod.sync;

import gjum.minecraft.mapsync.mod.MapSyncMod;
import gjum.minecraft.mapsync.mod.config.ServerConfig;
import gjum.minecraft.mapsync.mod.data.GameAddress;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.multiplayer.ServerData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GameContext {
	private static final Logger LOGGER = LoggerFactory.getLogger(GameContext.class);

	private final GameAddress gameAddress;
	private final ServerConfig gameConfig;
	private final SyncConnections syncConnections;

	private GameContext(
		final @NotNull GameAddress gameAddress,
		final @NotNull ServerConfig gameConfig
	) {
		this.gameAddress = Objects.requireNonNull(gameAddress);
		this.gameConfig = Objects.requireNonNull(gameConfig);
		this.syncConnections = new SyncConnections(this);
	}

	public void shutdown() {
		this.syncConnections.closeAll(true);
		if (DIMENSION_STATE.getAndSet(this, null) instanceof final DimensionState dimensionState) {
			dimensionState.shutDown();
		}
	}

	public @NotNull GameAddress getGameAddress() {
		return this.gameAddress;
	}

	public @NotNull ServerConfig getGameConfig() {
		return this.gameConfig;
	}

	public @NotNull SyncConnections getSyncConnections() {
		return this.syncConnections;
	}

	// ============================================================
	// Dimension State
	// ============================================================

	private volatile DimensionState dimensionState = null;
	private static final VarHandle DIMENSION_STATE; static {
		try {
			DIMENSION_STATE = MethodHandles.lookup().findVarHandle(GameContext.class, "dimensionState", DimensionState.class);
		}
		catch (final ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}
	public Optional<DimensionState> getDimensionState() {
		return Optional.ofNullable(this.dimensionState);
	}

	// ============================================================
	// Event Hooks
	// ============================================================

	private static volatile GameContext instance = null;
	private static final VarHandle INSTANCE; static {
		try {
			INSTANCE = MethodHandles.lookup().findStaticVarHandle(GameContext.class, "instance", GameContext.class);
		}
		catch (final ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}
	public static Optional<GameContext> get() {
		return Optional.ofNullable(instance);
	}

	public static void initEvents() {
		ClientPlayConnectionEvents.INIT.register((gameConnection, minecraft) -> {
			GameContext gameContext = null;
			try {
				if (!(gameConnection.getServerData() instanceof final ServerData serverData)) {
					LOGGER.error("Connection doesn't have server data yet... backing out");
					return;
				}
				final GameAddress gameAddress; {
					final String ip = serverData.ip;
					try {
						gameAddress = new GameAddress(ip);
					}
					catch (final Exception e) {
						LOGGER.error("Weirdly could not parse {} as a valid game address... backing out", ip, e);
						return;
					}
				}
				final ServerConfig gameConfig;
				try {
					gameConfig = ServerConfig.load(gameAddress);
				}
				catch (final Exception e) {
					LOGGER.error("Could not load game config for {}... backing out", gameAddress, e);
					return;
				}
				gameContext = new GameContext(
					gameAddress,
					gameConfig
				);
			}
			finally {
				if (INSTANCE.getAndSet(gameContext) instanceof final GameContext previous) {
					previous.shutdown();
				}
			}
		});
		ClientPlayConnectionEvents.JOIN.register((gameConnection, packetSender, minecraft) -> {
			if (instance instanceof final GameContext context) {
				MapSyncMod.handleGameConnection(minecraft, context);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((gameConnection, minecraft) -> {
			if (INSTANCE.getAndSet((Object) null) instanceof final GameContext context) {
				context.shutdown();
			}
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register((minecraft) -> {
			if (INSTANCE.getAndSet((Object) null) instanceof final GameContext context) {
				context.shutdown();
			}
		});
		ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((minecraft, level) -> {
			if (!(instance instanceof final GameContext gameContext)) {
				return;
			}
			final var dimensionState = new DimensionState(
				gameContext.getGameAddress(),
				level.dimension()
			);
			if (DIMENSION_STATE.getAndSet(gameContext, dimensionState) instanceof final DimensionState previous) {
				previous.shutDown();
			}
			MapSyncMod.handleDimensionChange(minecraft, level, gameContext);
		});
	}
}
