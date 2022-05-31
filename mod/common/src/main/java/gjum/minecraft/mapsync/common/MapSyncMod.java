package gjum.minecraft.mapsync.common;

import com.mojang.blaze3d.platform.InputConstants;
import gjum.minecraft.mapsync.common.config.ModConfig;
import gjum.minecraft.mapsync.common.config.ServerConfig;
import gjum.minecraft.mapsync.common.data.*;
import gjum.minecraft.mapsync.common.net.SyncClient;
import gjum.minecraft.mapsync.common.net.packet.*;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

import static gjum.minecraft.mapsync.common.Cartography.chunkTileFromLevel;

public abstract class MapSyncMod {
	public static final String VERSION = "${version}";

	private static final Minecraft mc = Minecraft.getInstance();

	public static final Logger logger = LogManager.getLogger(MapSyncMod.class);

	private static MapSyncMod INSTANCE;

	public static ModConfig modConfig;

	public static MapSyncMod getMod() {
		return INSTANCE;
	}

	private static final KeyMapping openGuiKey = new KeyMapping(
			"key.map-sync.openGui",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_COMMA,
			"category.map-sync"
	);

	private @NotNull List<SyncClient> syncClients = new ArrayList<>();

	/**
	 * Tracks state and render thread for current mc dimension.
	 * Never access this directly; always go through `getDimensionState()`.
	 */
	private @Nullable DimensionState dimensionState;

	/**
	 * Tracks configuration for current mc server.
	 * Never access this directly; always go through `getServerConfig()`.
	 */
	private @Nullable ServerConfig serverConfig;

	public MapSyncMod() {
		if (INSTANCE != null) throw new IllegalStateException("Constructor called twice");
		INSTANCE = this;
	}

	/**
	 * for example: 1.0.0+forge
	 */
	public abstract String getVersion();

	public abstract void registerKeyBinding(KeyMapping mapping);

	public void init() {
		registerKeyBinding(openGuiKey);

		modConfig = ModConfig.load();
		modConfig.saveNow(); // creates the default file if it doesn't exist yet
	}

	public void handleTick() {
		while (openGuiKey.consumeClick()) {
			mc.setScreen(new ModGui(mc.screen));
		}

		var dimensionState = getDimensionState();
		if (dimensionState != null) dimensionState.onTick();
	}

	public void handleConnectedToServer(ClientboundLoginPacket packet) {
		getSyncClients();
	}

	public void handleRespawn(ClientboundRespawnPacket packet) {
		debugLog("handleRespawn");
		// TODO tell sync server to only send chunks for this dimension now
	}

	/**
	 * only null when not connected to a server
	 */
	public @Nullable ServerConfig getServerConfig() {
		final ServerData currentServer = Minecraft.getInstance().getCurrentServer();
		if (currentServer == null) {
			serverConfig = null;
			return null;
		}
		String gameAddress = currentServer.ip;
		if (!gameAddress.contains(":")) gameAddress += ":25565";

		if (serverConfig == null) {
			serverConfig = ServerConfig.load(gameAddress);
		}
		return serverConfig;
	}

	public @NotNull List<SyncClient> getSyncClients() {
		var serverConfig = getServerConfig();
		if (serverConfig == null) return shutDownSyncClients();

		var syncServerAddresses = serverConfig.getSyncServerAddresses();
		if (syncServerAddresses.isEmpty()) return shutDownSyncClients();

		// will be filled with clients that are still wanted (address) and are still connected
		var existingClients = new HashMap<String, SyncClient>();

		for (SyncClient client : syncClients) {
			if (client.isShutDown) continue;
			// avoid reconnecting to same sync server, to keep shared state (expensive to resync)
			if (!client.gameAddress.equals(serverConfig.gameAddress)) {
				debugLog("Disconnecting sync client; different game server");
				client.shutDown();
			} else if (!syncServerAddresses.contains(client.address)) {
				debugLog("Disconnecting sync client; different sync address");
				client.shutDown();
			} else {
				existingClients.put(client.address, client);
			}
		}

		syncClients = syncServerAddresses.stream().map(address -> {
			var client = existingClients.get(address);
			if (client == null) client = new SyncClient(address, serverConfig.gameAddress);
			client.autoReconnect = true;
			return client;
		}).collect(Collectors.toList());

		return syncClients;
	}

	public List<SyncClient> shutDownSyncClients() {
		for (SyncClient client : syncClients) {
			client.shutDown();
		}
		syncClients.clear();
		return Collections.emptyList();
	}

	/**
	 * for current dimension
	 */
	public @Nullable DimensionState getDimensionState() {
		if (mc.level == null) return null;
		var serverConfig = getServerConfig();
		if (serverConfig == null) return null;

		if (dimensionState != null && dimensionState.dimension != mc.level.dimension()) {
			shutDownDimensionState();
		}
		if (dimensionState == null || dimensionState.hasShutDown) {
			dimensionState = new DimensionState(serverConfig.gameAddress, mc.level.dimension());
		}
		return dimensionState;
	}

	private void shutDownDimensionState() {
		if (dimensionState != null) {
			dimensionState.shutDown();
			dimensionState = null;
		}
	}

	/**
	 * an entire chunk was received from the mc server;
	 * send it to the map data server right away.
	 */
	public void handleMcFullChunk(int cx, int cz) {
		// TODO batch this up and send multiple chunks at once

		if (mc.level == null) return;
		// TODO disable in nether (no meaningful "surface layer")
		var dimensionState = getDimensionState();
		if (dimensionState == null) return;

		debugLog("received mc chunk: " + cx + "," + cz);

		var chunkTile = chunkTileFromLevel(mc.level, cx, cz);

		// TODO handle journeymap skipping chunks due to rate limiting - probably need mixin on render function
		if (RenderQueue.areAllMapModsMapping()) {
			dimensionState.setChunkTimestamp(chunkTile.chunkPos(), chunkTile.timestamp());
		}
		for (SyncClient client : getSyncClients()) {
			client.sendChunkTile(chunkTile);
		}
	}

	/**
	 * part of a chunk changed, and the chunk is likely to change again soon,
	 * so a ChunkTile update is queued, instead of updating instantly.
	 */
	public void handleMcChunkPartialChange(int cx, int cz) {
		// TODO update ChunkTile in a second or so; remember dimension in case it changes til then
	}

	public void handleSyncServerEncryptionSuccess() {
		debugLog("tcp encrypted");
		// TODO tell server our current dimension
	}

	public void handleRegionTimestamps(SRegionTimestamps packet, SyncClient client) {
		DimensionState dimension = getDimensionState();
		if (dimension == null) return;
		if (!dimension.dimension.location().toString().equals(packet.getDimension())) {
			return;
		}
		RegionTimestamp[] regions = packet.getTimestamps();
		ShortArrayList list = new ShortArrayList();
		for (RegionTimestamp region : regions) {
			RegionPos regionPos = new RegionPos(region.x(), region.z());
			long oldestChunkTs = dimension.getOldestChunkTsInRegion(regionPos);
			boolean requiresUpdate = region.timestamp() > oldestChunkTs;

			debugLog("region " + regionPos
					+ (requiresUpdate ? " requires update." : " is up to date.")
					+ " oldest client chunk ts: " + oldestChunkTs
					+ ", newest server chunk ts: " + region.timestamp());

			if (requiresUpdate) {
				list.add(region.x());
				list.add(region.z());
			}
		}

		client.send(new CRegionCatchup(packet.getDimension(), list.toShortArray()));
	}

	public void handleSharedChunk(ChunkTile chunkTile) {
		debugLog("received shared chunk: " + chunkTile.chunkPos());
		for (SyncClient syncClient : getSyncClients()) {
			syncClient.setServerKnownChunkHash(chunkTile.chunkPos(), chunkTile.dataHash());
		}

		var dimensionState = getDimensionState();
		if (dimensionState == null) return;
		dimensionState.processSharedChunk(chunkTile);
	}

	public void handleCatchupData(SCatchup packet) {
		var dimensionState = getDimensionState();
		if (dimensionState == null) return;
		debugLog("received catchup: " + packet.chunks.size() + " " + packet.chunks.get(0).syncClient.address);
		dimensionState.addCatchupChunks(packet.chunks);
	}

	public void requestCatchupData(List<CatchupChunk> chunks) {
		if (chunks == null || chunks.isEmpty()) {
			debugLog("not requesting more catchup: null/empty");
			return;
		}

		debugLog("requesting more catchup: " + chunks.size());
		var byServer = new HashMap<String, List<CatchupChunk>>();
		for (CatchupChunk chunk : chunks) {
			var list = byServer.computeIfAbsent(chunk.syncClient.address, (a) -> new ArrayList<>());
			list.add(chunk);
		}
		for (List<CatchupChunk> chunksForServer : byServer.values()) {
			SyncClient client = chunksForServer.get(0).syncClient;
			client.send(new CCatchupRequest(chunksForServer));
		}
	}

	public static void debugLog(String msg) {
		// we could also make use of slf4j's debug() but I don't know how to reconfigure that at runtime based on globalConfig
		if (modConfig.isShowDebugLog()) {
			logger.info(msg);
		}
	}
}
