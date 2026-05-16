package gjum.minecraft.mapsync.mod.server.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Caches `ign → UUID` lookups learned from successful Mojang authentications,
/// so a server operator can whitelist a player by IGN even if that player
/// hasn't authenticated in this session yet. Persists as a JSON object.
public final class UuidCache {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Map<String, UUID> byName = new HashMap<>();

	public synchronized @Nullable UUID lookup(
		final @NotNull String playerName
	) {
		return this.byName.get(playerName);
	}

	public synchronized void put(
		final @NotNull String playerName,
		final @NotNull UUID uuid
	) {
		this.byName.put(playerName, uuid);
	}

	public synchronized int size() {
		return this.byName.size();
	}

	/// Snapshot of UUID → IGN, built from the IGN → UUID cache. Used by
	/// `/mapsync whitelist list` to render entries with human-readable names.
	/// If the same UUID has cached multiple names (after a rename), the most
	/// recently inserted name wins — HashMap iteration order is undefined but
	/// the override on collision is deterministic for the caller.
	public synchronized @NotNull Map<UUID, String> namesByUuid() {
		final Map<UUID, String> out = new HashMap<>(this.byName.size());
		for (final var entry : this.byName.entrySet()) {
			out.put(entry.getValue(), entry.getKey());
		}
		return out;
	}

	public static @NotNull UuidCache loadOrCreate(
		final @NotNull Path path
	) throws Exception {
		final var out = new UuidCache();
		if (!Files.exists(path)) {
			out.save(path);
			return out;
		}
		final JsonObject raw = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
		for (final var entry : raw.entrySet()) {
			out.byName.put(entry.getKey(), UUID.fromString(entry.getValue().getAsString()));
		}
		return out;
	}

	public synchronized void save(
		final @NotNull Path path
	) throws Exception {
		final Path parent = path.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		final JsonObject object = new JsonObject();
		for (final var entry : this.byName.entrySet()) {
			object.addProperty(entry.getKey(), entry.getValue().toString());
		}
		Files.writeString(path, GSON.toJson(object));
	}
}
