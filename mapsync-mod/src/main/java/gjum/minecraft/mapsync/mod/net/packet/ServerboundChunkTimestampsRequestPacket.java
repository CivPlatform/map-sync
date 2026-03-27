package gjum.minecraft.mapsync.mod.net.packet;

import gjum.minecraft.mapsync.mod.data.RegionPos;
import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.buffers.BufferWriter;
import gjum.minecraft.mapsync.mod.utils.Assertions;
import org.jetbrains.annotations.NotNull;

/// You send this in response to a [ClientboundRegionTimestampsPacket], listing all the regions you'd like the server to
/// elaborate on. You should expect a [ClientboundChunkTimestampsResponsePacket].
///
/// - Prev: [ClientboundRegionTimestampsPacket]
/// - Next: [ClientboundChunkTimestampsResponsePacket]
public record ServerboundChunkTimestampsRequestPacket(
	@NotNull String dimension,
	@NotNull RegionPos region
) implements Packet {
	public static final int PACKET_ID = 8;

	public ServerboundChunkTimestampsRequestPacket {
		Assertions.assertNotNull(dimension);
		Assertions.assertNotNull(region);
	}

	@Override
	public void write(
		final @NotNull BufferWriter writer
	) throws Exception {
		writer.writeString(this.dimension());
		writer.writeInt16((short) this.region().x());
		writer.writeInt16((short) this.region().z());
	}
}
