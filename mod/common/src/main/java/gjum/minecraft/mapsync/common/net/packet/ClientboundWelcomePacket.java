package gjum.minecraft.mapsync.common.net.packet;

import gjum.minecraft.mapsync.common.net.Packet;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

/**
 * You will receive this in response to {@link ServerboundHandshakePacket}, and
 * will expect a {@link ServerboundAuthResponsePacket} in response.
 */
public record ClientboundWelcomePacket() implements Packet {
	public static final int PACKET_ID = 9;

	public static ClientboundWelcomePacket read(
		final @NotNull ByteBuf buf
	) {
		return new ClientboundWelcomePacket();
	}
}
