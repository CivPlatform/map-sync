package gjum.minecraft.mapsync.common.net.packet;

import gjum.minecraft.mapsync.common.net.Packet;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * This is sent to the server in response to a {@link ClientboundAuthRequestPacket},
 * after which, if the connection persists, you are considered authenticated
 * with the server. You should then receive a {@link ClientboundRegionTimestampsPacket}.
 *
 * @param clientSecret encrypted with server's public key
 */
public record ServerboundAuthResponsePacket(
	byte @NotNull [] clientSecret
) implements Packet {
	public static final int PACKET_ID = 3;

	public ServerboundAuthResponsePacket {
		Objects.requireNonNull(clientSecret);
	}

	@Override
	public void write(
		final @NotNull ByteBuf out
	) {
		Packet.writeIntLengthByteArray(out, clientSecret());
	}
}
