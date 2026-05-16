package gjum.minecraft.mapsync.mod.server.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/// JSON-backed UUID allowlist for the bundled server. Persisted as an array
/// of UUID strings — same format as mapsync-server's whitelist.json. Phase 3
/// will additionally fold the Minecraft server's own whitelist/ops into this
/// at runtime; the file remains the persistent ground truth.
public final class Whitelist {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Set<UUID> uuids = new HashSet<>();

	public boolean isWhitelisted(
		final @NotNull UUID uuid
	) {
		synchronized (this.uuids) {
			return this.uuids.contains(uuid);
		}
	}

	public boolean add(
		final @NotNull UUID uuid
	) {
		synchronized (this.uuids) {
			return this.uuids.add(uuid);
		}
	}

	public boolean remove(
		final @NotNull UUID uuid
	) {
		synchronized (this.uuids) {
			return this.uuids.remove(uuid);
		}
	}

	public int size() {
		synchronized (this.uuids) {
			return this.uuids.size();
		}
	}

	/// Replaces the live whitelist contents in place. Used by
	/// `/mapsync whitelist reload` to pick up manual edits to whitelist.json
	/// without invalidating references the websocket auth handler holds.
	public void replaceAll(
		final @NotNull Collection<UUID> replacement
	) {
		synchronized (this.uuids) {
			this.uuids.clear();
			this.uuids.addAll(replacement);
		}
	}

	/// Snapshot of the current whitelisted UUIDs. Used by reload to source
	/// values from a freshly-loaded Whitelist into the live one.
	public @NotNull Set<UUID> snapshot() {
		synchronized (this.uuids) {
			return new HashSet<>(this.uuids);
		}
	}

	public static @NotNull Whitelist loadOrCreate(
		final @NotNull Path path
	) throws Exception {
		final var out = new Whitelist();
		if (!Files.exists(path)) {
			out.save(path);
			return out;
		}
		final JsonArray raw = JsonParser.parseString(Files.readString(path)).getAsJsonArray();
		for (final var element : raw) {
			out.uuids.add(UUID.fromString(element.getAsString()));
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
		final JsonArray array = new JsonArray();
		synchronized (this.uuids) {
			for (final UUID uuid : this.uuids) {
				array.add(uuid.toString());
			}
		}
		Files.writeString(path, GSON.toJson(array));
	}
}
