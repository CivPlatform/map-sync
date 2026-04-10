package gjum.minecraft.mapsync.mod.config;

import com.google.gson.annotations.Expose;

import net.minecraft.client.Minecraft;

public class ModConfig extends JsonConfig {
	@Expose
	private boolean showDebugLog = false;

	public boolean isShowDebugLog() {
		return showDebugLog;
	}

	public void setShowDebugLog(boolean value) {
		showDebugLog = value;
		saveLater();
	}

	@Expose
	private int catchupWatermark = 100;

	public int getCatchupWatermark() {
		return catchupWatermark;
	}

	public void setCatchupWatermark(int value) {
		catchupWatermark = value;
		saveLater();
	}

	public static ModConfig load() {
		return ModConfig.load(
			getConfigDir().resolve("mod-config.json").toFile(),
			ModConfig.class
		);
	}
}
