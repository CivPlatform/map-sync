package gjum.minecraft.mapsync.mod.net.packet;

import gjum.minecraft.mapsync.mod.data.RegionTimestamp;
import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.buffers.BufferReader;
import gjum.minecraft.mapsync.mod.utils.Assertions;
import org.jetbrains.annotations.NotNull;

/// This is the packet for the first-stage of the synchronisation process. It's sent immediately after you've been
/// authenticated. You should respond with a [ServerboundChunkTimestampsRequestPacket].
///
/// - Next: [ServerboundChunkTimestampsRequestPacket]
public record ClientboundRegionTimestampsPacket(
	@NotNull String dimension,
	@NotNull RegionTimestamp timestamp
) implements Packet {
	public static final int PACKET_ID = 7;

	public ClientboundRegionTimestampsPacket {
		Assertions.assertNotNull(dimension);
		Assertions.assertNotNull(timestamp);
	}

	public static @NotNull Packet read(
		final @NotNull BufferReader reader
	) throws Exception {
		return new ClientboundRegionTimestampsPacket(
			reader.readString(),
			new RegionTimestamp(
				(short) reader.readInt16(),
				(short) reader.readInt16(),
				reader.readInt64()
			)
		);
	}
}
