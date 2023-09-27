package gjum.minecraft.mapsync.common.net.packet;

import gjum.minecraft.mapsync.common.net.Packet;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

/**
 * This should be sent to the server <i>IMMEDIATELY</i> upon connection. If the
 * server accepts the connection, you will receive a {@link ClientboundEncryptionRequestPacket}.
 */
public class ServerboundHandshakePacket implements Packet {
	public static final int PACKET_ID = 1;

	public final @NotNull String modVersion;
	public final @NotNull String username;
	public final @NotNull String gameAddress;
	public final @NotNull String world;

	public ServerboundHandshakePacket(@NotNull String modVersion, @NotNull String username, @NotNull String gameAddress, @NotNull String world) {
		this.modVersion = modVersion;
		this.username = username;
		this.gameAddress = gameAddress;
		this.world = world;
	}

	@Override
	public void write(@NotNull ByteBuf out) {
		Packet.writeUtf8String(out, modVersion);
		Packet.writeUtf8String(out, username);
		Packet.writeUtf8String(out, gameAddress);
		Packet.writeUtf8String(out, world);
	}

	@Override
	public String toString() {
		return "CHandshake{" +
				"version='" + modVersion + '\'' +
				" username='" + username + '\'' +
				" gameAddress='" + gameAddress + '\'' +
				'}';
	}
}
