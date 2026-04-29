package gjum.minecraft.mapsync.mod.net.packet;

import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.buffers.BufferWriter;
import gjum.minecraft.mapsync.mod.utils.Assertions;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/// The client should send this to the server:
///
/// 1. Whenever the player changes dimension (such as going through a portal)
/// 2. Whenever a new sync connection is made while the player is already in-game.
///
/// - Prev: [ClientboundWelcomePacket]
/// - Next: [ClientboundRegionTimestampsPacket]
public record ServerboundDimensionChangePacket(
	@NotNull Identifier dimension
) implements Packet {
	public static final int PACKET_ID = 10;

	public ServerboundDimensionChangePacket {
		Assertions.assertNotNull(dimension);
	}

	@Override
	public void write(
		final @NotNull BufferWriter writer
	) throws Exception {
		writer.writeString(this.dimension().toString());
	}
}
