package gjum.minecraft.mapsync.mod.config;

import com.google.gson.annotations.Expose;
import gjum.minecraft.mapsync.mod.data.GameAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

public final class ServerConfig extends JsonConfig {
	@Expose
	private ArrayList<String> syncServerAddresses = new ArrayList<>();

	public @NotNull List<@NotNull String> getSyncServerAddresses() {
		return this.syncServerAddresses.stream()
			.map(String::trim)
			.filter(StringUtils::isNotEmpty)
			.map((address) -> address.contains(":") ? address : (address + ":12312"))
			.distinct()
			.collect(Collectors.toCollection(ArrayList::new));
	}

	public void setSyncServerAddresses(
		final @NotNull List<String> syncAddresses
	) {
		this.syncServerAddresses = new ArrayList<>(syncAddresses);
	}

	@Override
	public void resetToDefaults() {
		this.setSyncServerAddresses(List.of(
			"ws://localhost:12312"
		));
	}

	public static @NotNull ServerConfig load(
		final @NotNull GameAddress gameAddress
	) {
		return load(
			getConfigDir().resolve("%s.json".formatted(gameAddress.asFsName())).toFile(),
			ServerConfig.class
		);
	}
}
