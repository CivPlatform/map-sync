package gjum.minecraft.mapsync.common.net.packet;

import gjum.minecraft.mapsync.common.net.Packet;
import io.netty.buffer.ByteBuf;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CHandshake extends Packet {
	public static final int PACKET_ID = 1;

	@Nullable
	public final String modVersion;
	@Nonnull
	public final String username;
	@Nullable
	public final String gameAddress;

	public CHandshake(@Nullable String modVersion, @Nullable String username, @Nullable String gameAddress) {
		this.modVersion = modVersion;
		this.username = username == null ? "" : username;
		this.gameAddress = gameAddress;
	}

	public static Packet read(ByteBuf buf) {
		return new CHandshake(
				readString(buf),
				readString(buf),
				readString(buf));
	}

	@Override
	public void write(ByteBuf out) {
		writeString(out, modVersion);
		writeString(out, username);
		writeString(out, gameAddress);
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
