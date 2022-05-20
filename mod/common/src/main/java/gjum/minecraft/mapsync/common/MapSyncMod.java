package gjum.minecraft.mapsync.common;

import com.mojang.blaze3d.platform.InputConstants;
import gjum.minecraft.mapsync.common.data.ChunkTile;
import gjum.minecraft.mapsync.common.integration.JourneyMapHelper;
import gjum.minecraft.mapsync.common.net.TcpClient;
import gjum.minecraft.mapsync.common.net.packet.ChunkTilePacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.HashMap;

import static gjum.minecraft.mapsync.common.Cartography.chunkTileFromLevel;

public abstract class MapSyncMod {
	public static final String VERSION = "1.0.0";

	private static final Minecraft mc = Minecraft.getInstance();

	public static MapSyncMod INSTANCE;

	private static final KeyMapping openGuiKey = new KeyMapping(
			"key.map-sync.openGui",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_COMMA,
			"category.map-sync"
	);

	private @Nullable TcpClient syncClient;

	/**
	 * for current dimension
	 */
	private HashMap<ChunkPos, byte[]> serverKnownChunkHashes = new HashMap<>();

	public static MapSyncMod getMod() {
		return INSTANCE;
	}

	public MapSyncMod() {
		if (INSTANCE != null) throw new IllegalStateException("Constructor called twice");
		INSTANCE = this;
	}

	public abstract void registerKeyBinding(KeyMapping mapping);

	public void init() {
		registerKeyBinding(openGuiKey);
	}

	public void handleTick() {
		while (openGuiKey.consumeClick()) {
			// XXX handle key press
		}
	}

	public void handleConnectedToServer(ClientboundLoginPacket packet) {
		final ServerData currentServer = Minecraft.getInstance().getCurrentServer();
		if (currentServer == null) return;
		String gameAddress = currentServer.ip;

		@Nullable String syncServerAddress = "localhost:12312"; // XXX

		if (syncClient != null) {
			// avoid reconnecting to same sync server, to keep shared state (expensive to resync)
			if (!syncClient.gameAddress.equals(gameAddress)
					|| !syncClient.address.equals(syncServerAddress)
			) {
				syncClient.shutDown();
				syncClient = null;
				serverKnownChunkHashes.clear();
			}
		}

		if (syncClient == null || syncClient.isShutDown) {
			serverKnownChunkHashes.clear();
			syncClient = new TcpClient(syncServerAddress, gameAddress);
		}
	}

	// TODO on mc disconnect, tell server our dimension is null, so it doesn't send full chunks that we can't use

	public void handleRespawn(ClientboundRespawnPacket packet) {
		// TODO handle dimensions correctly
	}

	/**
	 * an entire chunk was received from the mc server;
	 * send it to the map data server right away.
	 */
	public void handleMcFullChunk(int cx, int cz) {
		if (mc.level == null) return;
		// TODO disable in nether (no meaningful "surface layer")

		var chunkTile = chunkTileFromLevel(mc.level, cx, cz);
		sendChunkTileToMapDataServer(chunkTile);
	}

	/**
	 * part of a chunk changed, and the chunk is likely to change again soon,
	 * so a ChunkTile update is queued, instead of updating instantly.
	 */
	public void handleMcChunkPartialChange(int cx, int cz) {
		// TODO update ChunkTile in a second or so; remember dimension in case it changes til then
	}

	/**
	 * if the server already has the chunk (same hash), the chunk is dropped.
	 * if still connecting, the chunk is queued, replacing any previous chunk at the same pos.
	 * if connection failed, the chunk is dropped.
	 */
	private void sendChunkTileToMapDataServer(ChunkTile chunkTile) {
		if (syncClient == null) return;
		var serverKnownHash = serverKnownChunkHashes.get(chunkTile.chunkPos());
		if (Arrays.equals(chunkTile.dataHash(), serverKnownHash)) {
			return; // server already has this chunk
		}
		boolean sent = syncClient.send(new ChunkTilePacket(chunkTile));
		if (sent) serverKnownChunkHashes.put(chunkTile.chunkPos(), chunkTile.dataHash());
		// else: send again next time chunk loads
	}

	public void handleSyncServerConnected() {

	}

	public void handleSyncServerEncryptionSuccess() {

	}

	public void handleSharedChunk(ChunkTile chunkTile) {
		if (mc.level == null) return;
		if (chunkTile.dimension() != mc.level.dimension()) {
			return;
		}

		serverKnownChunkHashes.put(chunkTile.chunkPos(), chunkTile.dataHash());

		if (mc.level.getChunkSource().hasChunk(chunkTile.x(), chunkTile.z())) {
			return; // don't update loaded chunks
		}
		JourneyMapHelper.updateWithChunkTile(chunkTile);
	}
}
