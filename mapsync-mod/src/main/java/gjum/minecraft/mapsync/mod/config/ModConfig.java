package gjum.minecraft.mapsync.mod.config;

import com.google.gson.annotations.Expose;
import org.jetbrains.annotations.NotNull;

public final class ModConfig extends JsonConfig {
	@Expose
	private boolean showDebugLog = false;

	public boolean isShowDebugLog() {
		return this.showDebugLog;
	}

	public void setShowDebugLog(
		final boolean value
	) {
		this.showDebugLog = value;
	}

	@Expose
	private int catchupWatermark = 100;

	public int getCatchupWatermark() {
		return Math.max(1, this.catchupWatermark);
	}

	public void setCatchupWatermark(
		final int value
	) {
		this.catchupWatermark = value;
	}

	@Override
	protected void resetToDefaults() {
		this.showDebugLog = false;
		this.catchupWatermark = 100;
	}

	public static @NotNull ModConfig load() {
		return ModConfig.load(
			getConfigDir().resolve("mod-config.json").toFile(),
			ModConfig.class
		);
	}
}
