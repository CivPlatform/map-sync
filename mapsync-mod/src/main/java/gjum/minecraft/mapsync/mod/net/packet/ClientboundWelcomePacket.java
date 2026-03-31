package gjum.minecraft.mapsync.mod.net.packet;

import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.buffers.BufferReader;
import org.jetbrains.annotations.NotNull;

/// This is sent by the server to indicate a successful connection: that the client can begin sending chunk data. The
/// server will immediately follow up this packet with a [ClientboundRegionTimestampsPacket].
///
/// - Prev: [ServerboundIdentityResponsePacket]
/// - Next: [ClientboundRegionTimestampsPacket]
public record ClientboundWelcomePacket() implements Packet {
	public static final int PACKET_ID = 9;

	public static @NotNull Packet read(
		final @NotNull BufferReader reader
	) throws Exception {
		return new ClientboundWelcomePacket();
	}
}
