package gjum.minecraft.mapsync.common.config;

import com.google.gson.annotations.Expose;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.nio.file.Path;

public class ModConfig extends JsonConfig {
	@Expose
	private boolean showDebugLog = true; // XXX false on release

	public boolean isShowDebugLog() {
		return showDebugLog;
	}

	public void setShowDebugLog(boolean value) {
		showDebugLog = value;
		saveLater();
	}

	public static ModConfig load() {
		final String mcRoot = Minecraft.getInstance().gameDirectory.getAbsolutePath();
		var dir = Path.of(mcRoot, "MapSync").toFile();
		dir.mkdirs();
		return ModConfig.load(new File(dir, "mod-config.json"), ModConfig.class);
	}
}
