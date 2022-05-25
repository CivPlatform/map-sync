package gjum.minecraft.mapsync.common.net.packet;

import gjum.minecraft.mapsync.common.net.Packet;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

import static gjum.minecraft.mapsync.common.Utils.readStringFromBuf;
import static gjum.minecraft.mapsync.common.Utils.writeStringToBuf;

public class CHandshake extends Packet {
	public static final int PACKET_ID = 1;

	public final @NotNull String modVersion;
	public final @NotNull String username;
	public final @NotNull String gameAddress;
	public final @NotNull String lastTimestamp;

	public CHandshake(@NotNull String modVersion, @NotNull String username, @NotNull String gameAddress, @NotNull String lastTimestamp) {
		this.modVersion = modVersion;
		this.username = username;
		this.gameAddress = gameAddress;
		this.lastTimestamp = lastTimestamp;
	}

	public static Packet read(ByteBuf buf) {
		return new CHandshake(
				readStringFromBuf(buf),
				readStringFromBuf(buf),
				readStringFromBuf(buf),
				readStringFromBuf(buf));
	}

	@Override
	public void write(ByteBuf out) {
		writeStringToBuf(out, modVersion);
		writeStringToBuf(out, username);
		writeStringToBuf(out, gameAddress);
		writeStringToBuf(out, lastTimestamp);
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
