package gjum.minecraft.mapsync.mod.server;

import gjum.minecraft.mapsync.mod.server.config.MapSyncConfig;
import gjum.minecraft.mapsync.mod.server.config.UuidCache;
import gjum.minecraft.mapsync.mod.server.config.Whitelist;
import gjum.minecraft.mapsync.mod.server.db.MapSyncDatabase;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
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
	private static volatile @Nullable MapSyncServerState instance;

	private final @NotNull Path dataDir;
	private final @NotNull MapSyncConfig config;
	private final @NotNull Whitelist whitelist;
	private final @NotNull UuidCache uuidCache;
	private final @NotNull MapSyncDatabase database;

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
		this.database.close();
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
