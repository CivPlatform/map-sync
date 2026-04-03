package gjum.minecraft.mapsync.mod.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;

public class ServerConfig extends JsonConfig {
	public String gameAddress;

	@Expose
	private @NotNull List<String> syncServerAddresses = new ArrayList<>();

	public @NotNull List<String> getSyncServerAddresses() {
		return syncServerAddresses;
	}

	public void setSyncServerAddresses(@NotNull List<String> addresses) {
		syncServerAddresses = addresses.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(address -> !address.isEmpty())
				.map(address -> address.contains(":") ? address : (address + ":12312"))
				.collect(Collectors.toCollection(ArrayList::new));

		saveLater();
	}

	public static ServerConfig load(String gameAddress) {
		final String mcRoot = Minecraft.getInstance().gameDirectory.getAbsolutePath();
		var dir = Path.of(mcRoot, "MapSync", gameAddress.replaceAll(":", "~")).toFile();
		dir.mkdirs();
		var conf = load(new File(dir, "server-config.json"), ServerConfig.class);
		conf.gameAddress = gameAddress;

		loadDefaults(conf);

		conf.syncServerAddresses = conf.syncServerAddresses.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(address -> !address.isEmpty())
				.collect(Collectors.toCollection(ArrayList::new));

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
		if (conf.syncServerAddresses.isEmpty() && defaults.syncServerAddresses != null) {
			conf.setSyncServerAddresses(defaults.syncServerAddresses);
		}
	}
}
