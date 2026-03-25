package gjum.minecraft.mapsync.mod.net.packet;

import gjum.minecraft.mapsync.mod.net.IntType;
import gjum.minecraft.mapsync.mod.net.Packet;
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
	public final @NotNull String dimension;

	public ServerboundHandshakePacket(@NotNull String modVersion, @NotNull String username, @NotNull String gameAddress, @NotNull String dimension) {
		this.modVersion = modVersion;
		this.username = username;
		this.gameAddress = gameAddress;
		this.dimension = dimension;
	}

	@Override
	public void write(@NotNull ByteBuf out) {
		Packet.writeLengthPrefixedString(out, IntType.U8, modVersion);
		Packet.writeLengthPrefixedString(out, IntType.U8, username);
		Packet.writeLengthPrefixedString(out, IntType.U8, gameAddress);
		Packet.writeLengthPrefixedString(out, IntType.U8, dimension);
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
