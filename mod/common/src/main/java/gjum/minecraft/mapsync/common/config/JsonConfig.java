package gjum.minecraft.mapsync.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Timer;
import java.util.TimerTask;

/**
 * subclasses must have constructor without args, to create default config
 */
public class JsonConfig {
	private static final Gson GSON = new GsonBuilder()
			.excludeFieldsWithoutExposeAnnotation()
			.setPrettyPrinting()
			.create();

	private static final Timer timer = new Timer();
	private static long saveLaterTimeout = 300;
	private long lastSaveTime = 0;

	protected File configFile;

	/**
	 * Doesn't save any newly created config; for that, call `saveNow()`.
	 */
	public static <T extends JsonConfig> @NotNull T load(@NotNull File file, Class<T> clazz) {
		try (FileReader reader = new FileReader(file)) {
			T config = GSON.fromJson(reader, clazz);
			config.configFile = file;
			System.out.println("[map-sync] Loaded existing " + file);
			return config;
		} catch (FileNotFoundException ignored) {
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			final T config = clazz.getConstructor().newInstance();
			config.configFile = file;
			System.out.println("[map-sync] Created default " + file);
			return config;
		} catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
		         InvocationTargetException ex) {
			throw new IllegalArgumentException(ex);
		}
	}

	public void saveLater() {
		final long originalSaveRequestTime = System.currentTimeMillis();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				if (lastSaveTime > originalSaveRequestTime) return; // already saved while waiting
				saveNow();
			}
		}, saveLaterTimeout);
	}

	synchronized
	public void saveNow() {
		try {
			lastSaveTime = System.currentTimeMillis();
			System.out.println("Saving " + getClass().getSimpleName() + " to " + configFile);
			configFile.getParentFile().mkdirs();
			String json = GSON.toJson(this);
			FileOutputStream fos = new FileOutputStream(configFile);
			fos.write(json.getBytes());
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
