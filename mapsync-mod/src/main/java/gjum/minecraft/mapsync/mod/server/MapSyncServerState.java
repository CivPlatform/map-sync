package gjum.minecraft.mapsync.mod.server;

import com.mojang.authlib.GameProfile;
import gjum.minecraft.mapsync.mod.server.config.MapSyncConfig;
import gjum.minecraft.mapsync.mod.server.config.UuidCache;
import gjum.minecraft.mapsync.mod.server.config.Whitelist;
import gjum.minecraft.mapsync.mod.server.db.MapSyncDatabase;
import gjum.minecraft.mapsync.mod.server.net.MapSyncWsServer;
import gjum.minecraft.mapsync.mod.server.net.ProtocolHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.server.players.UserWhiteListEntry;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Lifecycle-bound holder for the bundled server's live state: config,
/// whitelist, UUID cache, and database connection. One instance per
/// running Minecraft server; opened on `SERVER_STARTING` and closed on
/// `SERVER_STOPPING` by {@link MapSyncServerMod}. The future websocket
/// server (Phase 2E) reads from {@link #current()} to share the same
/// instances.
///
/// Data lives in `<world>/mapsync/` so a server with multiple worlds — or
/// a server that swaps worlds — gets a fresh sync database per world.
public final class MapSyncServerState implements AutoCloseable {
	private static final Logger logger = LogManager.getLogger(MapSyncServerState.class);
	private static volatile @Nullable MapSyncServerState instance;

	private final @NotNull Path dataDir;
	private final @NotNull MapSyncConfig config;
	private final @NotNull Whitelist whitelist;
	private final @NotNull UuidCache uuidCache;
	private final @NotNull MapSyncDatabase database;
	private final @NotNull ProtocolHandler protocolHandler;
	private volatile @Nullable MapSyncWsServer wsServer;

	private MapSyncServerState(
		final @NotNull Path dataDir,
		final @NotNull MapSyncConfig config,
		final @NotNull Whitelist whitelist,
		final @NotNull UuidCache uuidCache,
		final @NotNull MapSyncDatabase database
	) {
		this.dataDir = dataDir;
		this.config = config;
		this.whitelist = whitelist;
		this.uuidCache = uuidCache;
		this.database = database;
		this.protocolHandler = new ProtocolHandler(this);
	}

	public static @NotNull MapSyncServerState open(
		final @NotNull MinecraftServer server
	) throws Exception {
		final Path dataDir = server.getWorldPath(LevelResource.ROOT).resolve("mapsync");
		Files.createDirectories(dataDir);
		final MapSyncConfig config = MapSyncConfig.loadOrCreate(dataDir.resolve("config.json"));
		final Whitelist whitelist = Whitelist.loadOrCreate(dataDir.resolve("whitelist.json"));
		final UuidCache uuidCache = UuidCache.loadOrCreate(dataDir.resolve("uuid_cache.json"));
		final MapSyncDatabase database = MapSyncDatabase.openFile(dataDir.resolve("db.sqlite"));
		return new MapSyncServerState(dataDir, config, whitelist, uuidCache, database);
	}

	@Override
	public void close() throws Exception {
		final MapSyncWsServer running = this.wsServer;
		if (running != null) {
			try {
				running.stop(2000);
				logger.info("MapSync websocket server stopped");
			}
			catch (final Exception e) {
				logger.warn("Failed to stop MapSync websocket server cleanly", e);
			}
			this.wsServer = null;
		}
		this.protocolHandler.shutdown();
		this.database.close();
	}

	/// Constructs and starts the websocket server. Called from
	/// SERVER_STARTED so the player list and whitelist are already populated
	/// — the first authenticating client must be checked against a complete
	/// whitelist or it gets rejected for no reason.
	public void startWebsocket() {
		if (this.wsServer != null) {
			throw new IllegalStateException("websocket server already started");
		}
		final MapSyncWsServer ws = new MapSyncWsServer(this, this.protocolHandler);
		this.protocolHandler.install(ws);
		ws.start();
		this.wsServer = ws;
	}

	public @Nullable MapSyncWsServer wsServer() {
		return this.wsServer;
	}

	public @NotNull ProtocolHandler protocolHandler() {
		return this.protocolHandler;
	}

	public @NotNull Path dataDir() {
		return this.dataDir;
	}

	public @NotNull MapSyncConfig config() {
		return this.config;
	}

	public @NotNull Whitelist whitelist() {
		return this.whitelist;
	}

	public @NotNull UuidCache uuidCache() {
		return this.uuidCache;
	}

	public @NotNull MapSyncDatabase database() {
		return this.database;
	}

	/// Folds the Minecraft server's whitelist and ops list into MapSync's
	/// whitelist so operators don't manage two parallel allowlists. Called
	/// at SERVER_STARTING and again on every player join to pick up live
	/// `/whitelist add` and `/op` changes without a restart. New additions
	/// trigger a save; if nothing changed, no IO happens.
	public void importMinecraftAllowlist(
		final @NotNull MinecraftServer server
	) throws Exception {
		final PlayerList players = server.getPlayerList();
		int added = 0;
		for (final UserWhiteListEntry entry : players.getWhiteList().getEntries()) {
			final NameAndId user = entry.getUser();
			if (user != null && this.whitelist.add(user.id())) {
				added++;
				logger.info("Auto-whitelisted MC-whitelisted player {} ({})", user.name(), user.id());
			}
		}
		for (final ServerOpListEntry entry : players.getOps().getEntries()) {
			final NameAndId user = entry.getUser();
			if (user != null && this.whitelist.add(user.id())) {
				added++;
				logger.info("Auto-whitelisted operator {} ({})", user.name(), user.id());
			}
		}
		if (added > 0) {
			this.whitelist.save(this.dataDir.resolve("whitelist.json"));
		}
	}

	/// Records an IGN→UUID mapping learned from a player joining the
	/// Minecraft server. Lets operators later run `/mapsync whitelist add
	/// <name>` for someone who's already played here even if MapSync's
	/// websocket has never seen them.
	public void cachePlayerProfile(
		final @NotNull GameProfile profile
	) throws Exception {
		final UUID before = this.uuidCache.lookup(profile.name());
		if (profile.id().equals(before)) {
			return;
		}
		this.uuidCache.put(profile.name(), profile.id());
		this.uuidCache.save(this.dataDir.resolve("uuid_cache.json"));
	}

	public static @Nullable MapSyncServerState current() {
		return instance;
	}

	static void install(final @NotNull MapSyncServerState state) {
		instance = state;
	}

	static void uninstall() {
		instance = null;
	}
}
