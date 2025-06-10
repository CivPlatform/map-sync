package gjum.minecraft.mapsync.common.net.packet;

import gjum.minecraft.mapsync.common.net.Packet;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * You will receive this in response to {@link ServerboundHandshakePacket}, and
 * will expect a {@link ServerboundAuthResponsePacket} in response.
 */
public record ClientboundAuthRequestPacket(
	byte @NotNull [] serverSecret
) implements Packet {
	public static final int PACKET_ID = 2;

	public ClientboundAuthRequestPacket {
		Objects.requireNonNull(serverSecret);
	}

	public static ClientboundAuthRequestPacket read(
		final @NotNull ByteBuf buf
	) {
		return new ClientboundAuthRequestPacket(
			Packet.readIntLengthByteArray(buf)
		);
	}
}
