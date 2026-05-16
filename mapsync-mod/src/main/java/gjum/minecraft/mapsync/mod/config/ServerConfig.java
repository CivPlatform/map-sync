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

	@Expose
	private boolean autoConnect = false;

	/// Protects local map-mod data from being overwritten by the sync server.
	/// When true (default), MapSync refuses to render any chunk it hasn't
	/// previously seen — preserving whatever the player had in Xaero /
	/// JourneyMap / VoxelMap before MapSync was installed. Once the player
	/// physically loads a chunk in-game, MapSync records its timestamp and
	/// the chunk becomes eligible for sync-server updates under normal
	/// "newer wins" rules.
	///
	/// Flip off temporarily if you want MapSync to backfill data for chunks
	/// you've never visited. Persisted per-server because the trust decision
	/// is server-specific.
	@Expose
	private boolean preserveExistingMapData = true;

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

	public boolean shouldAutoConnect() {
		return this.autoConnect;
	}

	public void setAutoConnect(
		final boolean autoConnect
	) {
		this.autoConnect = autoConnect;
	}

	public boolean shouldPreserveExistingMapData() {
		return this.preserveExistingMapData;
	}

	public void setPreserveExistingMapData(
		final boolean preserveExistingMapData
	) {
		this.preserveExistingMapData = preserveExistingMapData;
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
