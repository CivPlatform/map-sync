package gjum.minecraft.mapsync.common;

import com.mojang.blaze3d.platform.InputConstants;
import gjum.minecraft.mapsync.common.config.ModConfig;
import gjum.minecraft.mapsync.common.config.ServerConfig;
import gjum.minecraft.mapsync.common.data.ChunkTile;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import static gjum.minecraft.mapsync.common.Cartography.chunkTileFromLevel;

public abstract class MapSyncMod {
	public static final String VERSION = "1.0.0";

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

	private @Nullable SyncClient syncClient;

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
	}

	public void handleConnectedToServer(ClientboundLoginPacket packet) {
		getSyncClient();
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

		if (serverConfig == null) {
			serverConfig = ServerConfig.load(gameAddress);
		}
		return serverConfig;
	}

	/**
	 * makes sure it's connected/connecting to the right address
	 */
	public @Nullable SyncClient getSyncClient() {
		var serverConfig = getServerConfig();
		if (serverConfig == null) return shutDownSyncClient();

		String syncServerAddress = serverConfig.getSyncServerAddress();
		if (syncServerAddress == null) return shutDownSyncClient();

		if (syncClient != null && syncClient.isShutDown) syncClient = null;

		if (syncClient != null) {
			// avoid reconnecting to same sync server, to keep shared state (expensive to resync)
			if (!syncClient.gameAddress.equals(serverConfig.gameAddress)) {
				debugLog("Disconnecting sync client; different game server");
				shutDownSyncClient();
			} else if (!syncClient.address.equals(syncServerAddress)) {
				debugLog("Disconnecting sync client; different sync address");
				shutDownSyncClient();
			}
		}

		if (syncClient == null || syncClient.isShutDown) {
			syncClient = new SyncClient(syncServerAddress, serverConfig.gameAddress);
		}

		syncClient.autoReconnect = true;
		return syncClient;
	}

	public SyncClient shutDownSyncClient() {
		if (syncClient != null) {
			syncClient.shutDown();
			syncClient = null;
		}
		return null;
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
		if (mc.level == null) return;
		// TODO disable in nether (no meaningful "surface layer")
		var dimensionState = getDimensionState();
		if (dimensionState == null) return;

		var chunkTile = chunkTileFromLevel(mc.level, cx, cz);

		// TODO handle journeymap skipping chunks due to rate limiting - probably need mixin on render function
		if (RenderQueue.areAllMapModsMapping()) {
			dimensionState.setChunkTimestamp(chunkTile.chunkPos(), chunkTile.timestamp());
		}
		var syncClient = getSyncClient();
		if (syncClient == null) return;
		syncClient.sendChunkTile(chunkTile);
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
		// TODO start requesting missed chunks
	}

	public void handleSharedChunk(ChunkTile chunkTile) {
		var syncClient = getSyncClient();
		if (syncClient == null) {
			// should not happen: we just received this packet from the sync client.
			// it would indicate a race condition with changing mc server or sync address.
			return;
		}
		syncClient.setServerKnownChunkHash(chunkTile.chunkPos(), chunkTile.dataHash());

		var dimensionState = getDimensionState();
		if (dimensionState == null) return;
		dimensionState.processSharedChunk(chunkTile);
	}

	public static void debugLog(String msg) {
		// we could also make use of slf4j's debug() but I don't know how to reconfigure that at runtime based on globalConfig
		if (modConfig.isShowDebugLog()) {
			logger.info(msg);
		}
	}
}
