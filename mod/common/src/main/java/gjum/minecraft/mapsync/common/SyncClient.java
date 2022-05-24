package gjum.minecraft.mapsync.common;

import gjum.minecraft.mapsync.common.net.TcpClient;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

/**
 * tracks shared state. inherits connection logic from TcpClient
 */
public class SyncClient extends TcpClient {
	private final HashMap<ChunkPos, byte[]> serverKnownChunkHashes = new HashMap<>();

	public SyncClient(@NotNull String address, @NotNull String gameAddress) {
		super(address, gameAddress);
	}

	public synchronized byte[] getServerKnownChunkHash(ChunkPos chunkPos) {
		return serverKnownChunkHashes.get(chunkPos);
	}

	public synchronized void setServerKnownChunkHash(ChunkPos chunkPos, byte[] hash) {
		serverKnownChunkHashes.put(chunkPos, hash);
	}
}
