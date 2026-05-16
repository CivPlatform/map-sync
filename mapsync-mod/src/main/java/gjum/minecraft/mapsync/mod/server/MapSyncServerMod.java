package gjum.minecraft.mapsync.mod.server;

import gjum.minecraft.mapsync.mod.data.GameAddress;
import gjum.minecraft.mapsync.mod.data.RegionTimestamp;
import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.buffers.BufferReader;
import gjum.minecraft.mapsync.mod.net.buffers.BufferWriter;
import gjum.minecraft.mapsync.mod.net.packet.ClientboundWelcomePacket;
import gjum.minecraft.mapsync.mod.net.packet.ServerboundHandshakePacket;
import gjum.minecraft.mapsync.mod.server.config.MapSyncConfig;
import gjum.minecraft.mapsync.mod.server.config.UuidCache;
import gjum.minecraft.mapsync.mod.server.config.Whitelist;
import gjum.minecraft.mapsync.mod.server.db.MapSyncDatabase;
import gjum.minecraft.mapsync.mod.server.db.StoredChunk;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Server-side (dedicated server) entrypoint. Phase 0 made the mod load on a
/// dedicated server; Phase 1 cements which classes are safe to use here. The
/// following packages are confirmed side-neutral and may be referenced from
/// any server-side code: `net.buffers`, `net.packet`, `data`, `utils`, plus
/// `net.auth` *except* {@code AuthProcess} (which still calls
/// {@code Minecraft.getInstance()} and is strictly client-only).
///
/// Phase 2 will plug the websocket server, persistence, and Mojang auth
/// directly into the Minecraft server lifecycle from here.
public final class MapSyncServerMod {
	public static final Logger logger = LogManager.getLogger(MapSyncServerMod.class);

	@ApiStatus.Internal
	public static void bootstrap() {
		runSharedProtocolSanityCheck();
		runPersistenceSanityCheck();
		runConfigSanityCheck();
		ServerLifecycleEvents.SERVER_STARTING.register((server) -> {
			try {
				final MapSyncServerState state = MapSyncServerState.open(server);
				MapSyncServerState.install(state);
				logger.info(
					"MapSync state opened at {} (port {}, auth {}, whitelist {})",
					state.dataDir(),
					state.config().port,
					state.config().auth,
					state.config().whitelist
				);
			}
			catch (final Exception e) {
				// Crash the server boot rather than continuing in a half-open
				// state — partial persistence is worse than refusing to start.
				throw new RuntimeException("Failed to open MapSync server state", e);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register((server) -> {
			final MapSyncServerState state = MapSyncServerState.current();
			if (state == null) {
				return;
			}
			MapSyncServerState.uninstall();
			try {
				state.close();
				logger.info("MapSync state closed");
			}
			catch (final Exception e) {
				logger.error("Failed to close MapSync state cleanly", e);
			}
		});
	}

	/// Round-trips both directions of the wire protocol through the shared
	/// encoder/decoder at server startup. Catches three classes of regressions
	/// early: (1) a client-only import sneaking into `net/buffers`,
	/// `net/packet`, `data`, or `utils` and breaking classloading; (2) a
	/// serverbound packet missing its server-side `read()`; (3) a clientbound
	/// packet missing its server-side `write()`. Any failure here aborts the
	/// dedicated server boot with a clear cause.
	private static void runSharedProtocolSanityCheck() {
		try {
			// Serverbound: encode (as the client would) → decode (as the server now must).
			final var sent = new ServerboundHandshakePacket(
				"phase-2-sanity-check",
				new GameAddress("localhost:25565")
			);
			final var clientToServer = new ByteArrayOutputStream();
			Packet.encodePacket(new BufferWriter(clientToServer), sent);
			final byte[] s2s = clientToServer.toByteArray();
			final Packet decoded = Packet.decodeServerbound(new BufferReader(ByteBuffer.wrap(s2s)));
			if (!(decoded instanceof ServerboundHandshakePacket received)) {
				throw new IllegalStateException("Serverbound round-trip lost type: " + decoded.getClass().getName());
			}
			if (!received.modVersion().equals(sent.modVersion())
				|| !received.gameAddress().address().equals(sent.gameAddress().address())) {
				throw new IllegalStateException("Serverbound round-trip payload mismatch");
			}

			// Clientbound: encode (the new path the server now uses).
			final var serverToClient = new ByteArrayOutputStream();
			Packet.encodePacket(new BufferWriter(serverToClient), new ClientboundWelcomePacket());
			final byte[] c2c = serverToClient.toByteArray();
			if (c2c.length != 1 || (c2c[0] & 0xFF) != ClientboundWelcomePacket.PACKET_ID) {
				throw new IllegalStateException(
					"Clientbound welcome did not encode to a single packet-id byte (got " + c2c.length + " bytes)"
				);
			}

			logger.info(
				"MapSync wire-protocol round-trip OK ({} bytes serverbound, {} bytes clientbound)",
				s2s.length, c2c.length
			);
		}
		catch (final Exception e) {
			throw new RuntimeException(
				"MapSync shared protocol sanity check failed — server-side classloading or wire-format regressed",
				e
			);
		}
	}

	/// Exercises the SQLite persistence layer against an in-memory database at
	/// startup. Failure here usually means sqlite-jdbc didn't get bundled into
	/// the jar correctly (no native libs at runtime) or the schema DDL has
	/// regressed. Either fails the dedicated server boot loudly.
	private static void runPersistenceSanityCheck() {
		// Driver class is loaded eagerly so JIJ failures surface here rather
		// than at the first real connection — DriverManager.getConnection
		// would otherwise just say "no suitable driver".
		try {
			Class.forName("org.sqlite.JDBC");
		}
		catch (final ClassNotFoundException e) {
			throw new RuntimeException("sqlite-jdbc driver missing from runtime classpath", e);
		}
		try (final var db = MapSyncDatabase.openInMemory()) {
			final String dim = "minecraft:overworld";
			final byte[] hash = new byte[20];
			for (int i = 0; i < hash.length; i++) hash[i] = (byte) i;
			final byte[] payload = "phase-2b-sanity-check".getBytes();
			db.storeChunkData(dim, 5, 7, UUID.randomUUID(), 1234L, 1, hash, payload);
			final Optional<StoredChunk> readBack = db.getChunkData(dim, 5, 7);
			if (readBack.isEmpty()) {
				throw new IllegalStateException("Stored chunk could not be read back");
			}
			if (readBack.get().timestamp() != 1234L
				|| readBack.get().dataVersion() != 1
				|| readBack.get().data().length != payload.length) {
				throw new IllegalStateException("Stored chunk read back with mismatched fields");
			}
			final List<RegionTimestamp> regions = db.getRegionTimestamps(dim);
			if (regions.size() != 1 || regions.get(0).timestamp() != 1234L) {
				throw new IllegalStateException("Region timestamp aggregation did not return the expected single row");
			}
			logger.info(
				"MapSync persistence round-trip OK (sqlite-jdbc driver: {})",
				DriverManager.getDriver("jdbc:sqlite::memory:").getClass().getName()
			);
		}
		catch (final Exception e) {
			throw new RuntimeException(
				"MapSync persistence sanity check failed — sqlite-jdbc or schema regressed",
				e
			);
		}
	}

	/// Round-trips the bundled-server config, whitelist, and UUID cache
	/// through a temp directory. Catches Gson packaging or JSON-schema
	/// regressions before the dedicated server has any chance to corrupt a
	/// real per-world config file in a later phase.
	private static void runConfigSanityCheck() {
		Path scratch = null;
		try {
			scratch = Files.createTempDirectory("mapsync-config-check");
			final var cfg = MapSyncConfig.loadOrCreate(scratch.resolve("config.json"));
			cfg.port = 12399;
			cfg.save(scratch.resolve("config.json"));
			final var reloaded = MapSyncConfig.loadOrCreate(scratch.resolve("config.json"));
			if (reloaded.port != 12399 || reloaded.auth != cfg.auth) {
				throw new IllegalStateException("Config did not round-trip");
			}

			final var wl = Whitelist.loadOrCreate(scratch.resolve("whitelist.json"));
			final var probe = UUID.randomUUID();
			wl.add(probe);
			wl.save(scratch.resolve("whitelist.json"));
			final var wlReloaded = Whitelist.loadOrCreate(scratch.resolve("whitelist.json"));
			if (!wlReloaded.isWhitelisted(probe) || wlReloaded.size() != 1) {
				throw new IllegalStateException("Whitelist did not round-trip");
			}

			final var cache = UuidCache.loadOrCreate(scratch.resolve("uuid_cache.json"));
			cache.put("Tester", probe);
			cache.save(scratch.resolve("uuid_cache.json"));
			final var cacheReloaded = UuidCache.loadOrCreate(scratch.resolve("uuid_cache.json"));
			if (!probe.equals(cacheReloaded.lookup("Tester"))) {
				throw new IllegalStateException("UUID cache did not round-trip");
			}
			logger.info("MapSync config round-trip OK (scratch dir: {})", scratch);
		}
		catch (final Exception e) {
			throw new RuntimeException(
				"MapSync config sanity check failed — JSON serialization regressed",
				e
			);
		}
		finally {
			if (scratch != null) {
				deleteRecursively(scratch);
			}
		}
	}

	private static void deleteRecursively(final @NotNull Path root) {
		try (final var paths = Files.walk(root)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
				.forEach((p) -> {
					try {
						Files.deleteIfExists(p);
					}
					catch (final Exception ignored) {
					}
				});
		}
		catch (final Exception ignored) {
		}
	}
}
