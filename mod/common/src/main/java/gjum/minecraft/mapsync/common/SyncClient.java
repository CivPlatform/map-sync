package gjum.minecraft.mapsync.common;

import gjum.minecraft.mapsync.common.data.ChunkTile;
import gjum.minecraft.mapsync.common.net.TcpClient;
import gjum.minecraft.mapsync.common.net.packet.ChunkTilePacket;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;

import static gjum.minecraft.mapsync.common.MapSyncMod.debugLog;

/**
 * tracks shared state. inherits connection logic from {@link TcpClient}
 */
public class SyncClient extends TcpClient {
	private final HashMap<ChunkPos, ChunkTile> inFlightChunks = new HashMap<>();

	private final HashMap<ChunkPos, byte[]> serverKnownChunkHashes = new HashMap<>();

	public SyncClient(@NotNull String address, @NotNull String gameAddress) {
		super(address, gameAddress);
	}

	public synchronized void sendChunkTile(ChunkTile chunkTile) {
		var serverKnownHash = getServerKnownChunkHash(chunkTile.chunkPos());
		if (Arrays.equals(chunkTile.dataHash(), serverKnownHash)) {
			debugLog("server already has chunk (hash) " + chunkTile.chunkPos());
			return; // server already has this chunk
		}

		send(new ChunkTilePacket(chunkTile));

		// assume packet will reach server eventually
		setServerKnownChunkHash(chunkTile.chunkPos(), chunkTile.dataHash());
	}

	public synchronized byte[] getServerKnownChunkHash(ChunkPos chunkPos) {
		return serverKnownChunkHashes.get(chunkPos);
	}

	public synchronized void setServerKnownChunkHash(ChunkPos chunkPos, byte[] hash) {
		serverKnownChunkHashes.put(chunkPos, hash);
	}
}
