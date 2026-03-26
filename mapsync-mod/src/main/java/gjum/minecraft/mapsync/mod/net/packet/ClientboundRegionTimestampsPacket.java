package gjum.minecraft.mapsync.mod.net.packet;

import gjum.minecraft.mapsync.mod.data.RegionTimestamp;
import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.buffers.BufferReader;

/**
 * This is the packet for the first-stage of the synchronisation process. It's
 * sent immediately after you've been authenticated. You should respond with a
 * {@link ServerboundChunkTimestampsRequestPacket}.
 */
public class ClientboundRegionTimestampsPacket implements Packet {
	public static final int PACKET_ID = 7;

	private final String dimension;

	private final RegionTimestamp timestamp;

	public ClientboundRegionTimestampsPacket(String dimension, RegionTimestamp timestamp) {
		this.dimension = dimension;
		this.timestamp = timestamp;
	}

	public String getDimension() {
		return dimension;
	}

	public RegionTimestamp getTimestamp() {
		return timestamp;
	}

	public static Packet read(BufferReader reader) throws Exception {
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
