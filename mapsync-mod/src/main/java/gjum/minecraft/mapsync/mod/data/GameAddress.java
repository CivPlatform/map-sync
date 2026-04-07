package gjum.minecraft.mapsync.mod.data;

import com.google.common.net.HostAndPort;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record GameAddress(
	@NotNull String address
) {
	public GameAddress {
		Objects.requireNonNull(address);
		address = HostAndPort.fromString(address).withDefaultPort(25565).toString();
	}

	public static @Nullable GameAddress from(
		final String address
	) {
		try {
			return new GameAddress(address);
		}
		catch (final Exception e) {
			return null;
		}
	}

	/// Returns a version of this game address that's valid for file/folder names.
	public @NotNull String asFsName() {
		return this.address().replace(":", "~");
	}
}
