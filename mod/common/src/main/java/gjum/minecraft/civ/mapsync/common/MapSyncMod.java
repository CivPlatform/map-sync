package gjum.minecraft.civ.mapsync.common;

import com.mojang.blaze3d.platform.InputConstants;
import gjum.minecraft.civ.mapsync.common.protocol.ChunkTile;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.*;
import org.lwjgl.glfw.GLFW;

public abstract class MapSyncMod {
	private static final Minecraft mc = Minecraft.getInstance();

	private static MapSyncMod INSTANCE;

	private static final KeyMapping openGuiKey = new KeyMapping(
			"key.civ-map-sync.openGui",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_COMMA,
			"category.civ-map-sync"
	);

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

	public void handleMcChunk(ClientboundLevelChunkWithLightPacket packet) {
		if (mc.level == null) return;
		// TODO disable in nether (no meaningful "surface layer")

		var chunkTile = ChunkTile.fromLevel(mc.level, packet.getX(), packet.getZ());
		// XXX send to server
	}

	public void handleSharedChunk(ChunkTile chunkTile) {
		if (mc.level == null) return;
		if (chunkTile.dimension() != mc.level.dimension()) return;

		// XXX update maps with chunkTile
	}
}
