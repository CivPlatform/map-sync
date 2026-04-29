package gjum.minecraft.mapsync.mod.net.packet;

import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.buffers.BufferReader;
import org.jetbrains.annotations.NotNull;

/// This is sent by the server to indicate a successful connection. The client should then inform the server of its
/// current dimension via [ServerboundDimensionChangePacket], after which the client can begin sending chunk data for
/// that dimension.
///
/// - Prev: [ServerboundIdentityResponsePacket]
/// - Next: [ServerboundDimensionChangePacket]
public record ClientboundWelcomePacket() implements Packet {
	public static final int PACKET_ID = 9;

	public static @NotNull Packet read(
		final @NotNull BufferReader reader
	) throws Exception {
		return new ClientboundWelcomePacket();
	}
}
