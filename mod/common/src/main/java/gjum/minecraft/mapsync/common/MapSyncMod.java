package gjum.minecraft.mapsync.common;

import com.mojang.blaze3d.platform.InputConstants;
import gjum.minecraft.mapsync.common.integration.JourneyMapHelper;
import gjum.minecraft.mapsync.common.protocol.ChunkHash;
import gjum.minecraft.mapsync.common.protocol.ChunkTile;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.world.level.ChunkPos;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;

import static gjum.minecraft.mapsync.common.Cartography.chunkTileFromLevel;

public abstract class MapSyncMod {
	private static final Minecraft mc = Minecraft.getInstance();

	private static MapSyncMod INSTANCE;

	private static final KeyMapping openGuiKey = new KeyMapping(
			"key.map-sync.openGui",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_COMMA,
			"category.map-sync"
	);

	/**
	 * for current dimension
	 */
	private HashMap<ChunkPos, String> serverKnownChunkHashes = new HashMap<>();

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

	public void handleConnectedToServer(ClientboundLoginPacket packet) {
		// XXX connect
	}

	public void handleRespawn(ClientboundRespawnPacket packet) {
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
		boolean hashKnownToServer = chunkTile.dataHash().equals(serverKnownChunkHashes.get(chunkTile.chunkPos()));
		if (hashKnownToServer) return; // server already has this chunk

		// XXX send to server
	}

	public void handleSharedChunkHash(ChunkHash chunkHash) {
		if (mc.level == null) return;
		if (chunkHash.dimension() != mc.level.dimension()) return;

		serverKnownChunkHashes.put(chunkHash.chunkPos(), chunkHash.dataHash());
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
