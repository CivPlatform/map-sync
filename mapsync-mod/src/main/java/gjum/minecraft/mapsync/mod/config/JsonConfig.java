package gjum.minecraft.mapsync.mod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class JsonConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonConfig.class);
	static final Gson GSON = new GsonBuilder()
		.excludeFieldsWithoutExposeAnnotation()
		.setPrettyPrinting()
		.create();

	protected File configFile;

	protected abstract void resetToDefaults();

	/// Doesn't save any newly created config; for that, call `saveNow()`.
	protected static <T extends JsonConfig> @NotNull T load(
		final @NotNull File configFile,
		final @NotNull Class<T> configClass
	) {
		Objects.requireNonNull(configFile);
		Objects.requireNonNull(configClass);
		T config;
		try (final var reader = new FileReader(configFile)) {
			config = GSON.fromJson(reader, configClass);
			config.configFile = configFile;
			LOGGER.info("Loaded existing {}", configFile);
			return config;
		}
		catch (final FileNotFoundException ignored) {}
		catch (final Exception e) {
			LOGGER.error("Failed to load config file {}", configFile, e);
		}
		try {
			config = configClass.getConstructor().newInstance();
			config.configFile = configFile;
			config.resetToDefaults();
			LOGGER.info("Created default {}", configFile);
			return config;
		}
		catch (final ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	synchronized
	public void save() {
		LOGGER.info("Saving {} to {}", getClass().getSimpleName(), this.configFile);
		try {
			Files.createDirectories(this.configFile.getParentFile().toPath());
			Files.write(
				this.configFile.toPath(),
				GSON.toJson(this).getBytes()
			);
		}
		catch (final Exception e) {
			LOGGER.error("Failed to save config file {}", this.configFile, e);
		}
	}

	protected static @NotNull Path getConfigDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("MapSync");
	}
}
