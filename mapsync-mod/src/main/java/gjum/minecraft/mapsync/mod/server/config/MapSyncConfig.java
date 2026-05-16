package gjum.minecraft.mapsync.mod.server.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

/// Bundled-server config, persisted as `config.json` alongside the world's
/// `db.sqlite`. Schema matches mapsync-server/src/metadata.ts so existing
/// configs migrate by copy. Fields are mutable on purpose — the future
/// `/mapsync` commands edit them in place.
public final class MapSyncConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public @NotNull String host = "0.0.0.0";
	public int port = 12312;
	public boolean whitelist = true;
	public boolean auth = true;

	public static @NotNull MapSyncConfig loadOrCreate(
		final @NotNull Path configPath
	) throws Exception {
		if (!Files.exists(configPath)) {
			final var defaults = new MapSyncConfig();
			defaults.save(configPath);
			return defaults;
		}
		// Read the file as a generic JsonObject first so unknown keys are
		// ignored and missing keys fall back to the field initializer values.
		final JsonObject raw = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
		final var config = new MapSyncConfig();
		if (raw.has("host")) config.host = raw.get("host").getAsString();
		if (raw.has("port")) config.port = raw.get("port").getAsInt();
		if (raw.has("whitelist")) config.whitelist = raw.get("whitelist").getAsBoolean();
		if (raw.has("auth")) config.auth = raw.get("auth").getAsBoolean();
		validate(config);
		return config;
	}

	public void save(
		final @NotNull Path configPath
	) throws Exception {
		validate(this);
		final Path parent = configPath.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.writeString(configPath, GSON.toJson(this));
	}

	private static void validate(
		final @NotNull MapSyncConfig config
	) {
		if (config.port <= 0 || config.port > 65535) {
			throw new IllegalArgumentException("port must be in (0, 65535], got " + config.port);
		}
		if (config.host.isBlank()) {
			throw new IllegalArgumentException("host must not be blank");
		}
	}
}
