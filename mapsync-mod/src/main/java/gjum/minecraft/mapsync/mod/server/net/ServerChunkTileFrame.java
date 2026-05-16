package gjum.minecraft.mapsync.mod.server.net;

import gjum.minecraft.mapsync.mod.net.buffers.BufferReader;
import gjum.minecraft.mapsync.mod.utils.MagicValues;
import java.nio.ByteBuffer;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/// Server-side representation of an incoming ChunkTilePacket. The client mod
/// parses ChunkTiles into BlockColumn[]s for rendering, but BlockColumn.read
/// reaches into Minecraft.getInstance().level — client-only state that
/// crashes a dedicated server boot. The bundled server therefore stops
/// parsing at the metadata boundary, keeps the column payload as opaque
/// bytes for storage, and relays the full original frame verbatim to other
/// clients.
public record ServerChunkTileFrame(
	@NotNull Identifier dimension,
	int chunkX,
	int chunkZ,
	long timestamp,
	int dataVersion,
	byte @NotNull [] dataHash,
	byte @NotNull [] columnBytes,
	byte @NotNull [] wireBytes
) {
	/// Decodes a ChunkTilePacket payload after the packet-id byte has already
	/// been consumed from `reader`. `raw` and `wireBytes` describe the same
	/// frame: `raw` is the live ByteBuffer the BufferReader is reading from
	/// (so we can grab the trailing column bytes after metadata reads), and
	/// `wireBytes` is a pristine copy captured before any reads, suitable
	/// for relaying to other clients without re-encoding.
	public static @NotNull ServerChunkTileFrame decodeAfterPacketId(
		final @NotNull BufferReader reader,
		final @NotNull ByteBuffer raw,
		final byte @NotNull [] wireBytes
	) throws Exception {
		final Identifier dimension = reader.readIdentifier();
		final int chunkX = reader.readInt32();
		final int chunkZ = reader.readInt32();
		final long timestamp = reader.readInt64();
		final int dataVersion = reader.readUnt16();
		final byte[] dataHash = reader.readBytesOfLength(MagicValues.SHA1_HASH_LENGTH);
		final int columnLen = raw.remaining();
		final byte[] columnBytes = new byte[columnLen];
		if (columnLen > 0) {
			raw.get(columnBytes);
		}
		return new ServerChunkTileFrame(
			dimension, chunkX, chunkZ, timestamp, dataVersion, dataHash, columnBytes, wireBytes
		);
	}
}
