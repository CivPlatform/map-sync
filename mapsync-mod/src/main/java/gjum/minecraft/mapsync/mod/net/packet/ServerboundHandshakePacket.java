package gjum.minecraft.mapsync.mod.net.packet;

import gjum.minecraft.mapsync.mod.net.Packet;
import gjum.minecraft.mapsync.mod.net.buffers.BufferWriter;
import gjum.minecraft.mapsync.mod.utils.Assertions;
import org.jetbrains.annotations.NotNull;

/// The client should send this to the server *IMMEDIATELY* upon a successful connection. The server should respond
/// with a [ClientboundEncryptionRequestPacket].
///
/// - Next: [ClientboundEncryptionRequestPacket]
public record ServerboundHandshakePacket(
	@NotNull String modVersion,
	@NotNull String username,
	@NotNull String gameAddress,
	@NotNull String dimension
) implements Packet {
	public static final int PACKET_ID = 1;

	public ServerboundHandshakePacket {
		Assertions.assertNotNull(modVersion);
		Assertions.assertNotNull(username);
		Assertions.assertNotNull(gameAddress);
		Assertions.assertNotNull(dimension);
	}

	@Override
	public void write(
		final @NotNull BufferWriter writer
	) throws Exception {
		writer.writeString(this.modVersion());
		writer.writeString(this.username());
		writer.writeString(this.gameAddress());
		writer.writeString(this.dimension());
	}
}
