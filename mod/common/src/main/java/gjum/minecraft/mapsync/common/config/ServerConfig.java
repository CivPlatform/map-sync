package gjum.minecraft.mapsync.common.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static gjum.minecraft.mapsync.common.MapSyncMod.getMod;

public class ServerConfig extends JsonConfig {
	public String gameAddress;

	@Expose
	private @Nullable String syncServerAddress = null;

	public @Nullable String getSyncServerAddress() {
		return syncServerAddress;
	}

	public void setSyncServerAddress(@Nullable String address) {
		if (address != null) {
			address = address.trim();
			if (address.isEmpty()) address = null;
		}
		syncServerAddress = address;
		saveLater();

		getMod().getSyncClient(); // trigger dis/connection if address changed
	}

	public static ServerConfig load(String gameAddress) {
		final String mcRoot = Minecraft.getInstance().gameDirectory.getAbsolutePath();
		var dir = Path.of(mcRoot, "MapSync", gameAddress.replaceAll(":", "~")).toFile();
		dir.mkdirs();
		var conf = load(new File(dir, "server-config.json"), ServerConfig.class);
		conf.gameAddress = gameAddress;

		loadDefaults(conf);

		return conf;
	}

	private static void loadDefaults(ServerConfig conf) {
		ServerConfig defaults;
		try (var input = ServerConfig.class.getResourceAsStream("/default-config.json")) {
			if (input == null) return;
			String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
			JsonObject root = new Gson().fromJson(json, JsonObject.class);
			JsonObject servers = root.get("servers").getAsJsonObject();
			JsonObject server = servers.get(conf.gameAddress).getAsJsonObject();
			defaults = GSON.fromJson(server, ServerConfig.class);
		} catch (IllegalStateException | NullPointerException ignored) {
			return;
		} catch (Throwable e) {
			e.printStackTrace();
			return;
		}
		if (conf.syncServerAddress == null) {
			conf.syncServerAddress = defaults.syncServerAddress;
		}
	}
}
