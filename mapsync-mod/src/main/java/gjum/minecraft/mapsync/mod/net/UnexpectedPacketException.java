package gjum.minecraft.mapsync.mod.net;

import org.jetbrains.annotations.NotNull;

public final class UnexpectedPacketException extends Exception {
	public UnexpectedPacketException(
		final @NotNull Object packet
	) {
		this("Unexpected packet [%s]!".formatted(
			packet.getClass().getName()
		));
	}

	public UnexpectedPacketException(
		final byte packetId
	) {
		this("Unexpected packet id [%d]!".formatted(
			packetId
		));
	}

	public UnexpectedPacketException(
		final @NotNull String message
	) {
		super(message);
	}
}
