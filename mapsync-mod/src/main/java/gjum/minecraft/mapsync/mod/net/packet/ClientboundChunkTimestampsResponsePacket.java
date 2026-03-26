package gjum.minecraft.mapsync.mod.net.packet;

import gjum.minecraft.mapsync.mod.data.CatchupChunk;
import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.buffers.BufferReader;
import gjum.minecraft.mapsync.mod.utils.Assertions;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/// The server will send this packet, containing an elaboration of chunk timestamps of a particular region as requested
/// via [ServerboundChunkTimestampsRequestPacket]. The client should respond with a [ServerboundCatchupRequestPacket]
/// if it finds any chunks with a timestamp newer than its own.
///
/// - Prev: [ServerboundChunkTimestampsRequestPacket]
/// - Next: [ServerboundCatchupRequestPacket]
public record ClientboundChunkTimestampsResponsePacket(
	@NotNull List<@NotNull CatchupChunk> chunks
) implements Packet {
	public static final int PACKET_ID = 5;

	public ClientboundChunkTimestampsResponsePacket {
		chunks = Assertions.assertNonNullList(chunks);
	}

	public static @NotNull ClientboundChunkTimestampsResponsePacket read(
		final @NotNull BufferReader reader
	) throws Exception {
		final ResourceKey<Level> dimension = reader.readResourceKey(Registries.DIMENSION);
		final int anchorChunkX = reader.readInt16() << 5;
		final int anchorChunkZ = reader.readInt16() << 5;
		final var chunks = new CatchupChunk[reader.readUnt10()];
		for (int i = 0; i < chunks.length; i++) {
			chunks[i] = new CatchupChunk(
				dimension,
				anchorChunkX + reader.readUnt5(),
				anchorChunkZ + reader.readUnt5(),
				reader.readInt64()
			);
		}
		return new ClientboundChunkTimestampsResponsePacket(
			List.of(chunks)
		);
	}
}
