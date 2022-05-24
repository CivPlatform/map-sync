package gjum.minecraft.mapsync.common;

import com.mojang.blaze3d.vertex.PoseStack;
import gjum.minecraft.mapsync.common.config.ServerConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

import static gjum.minecraft.mapsync.common.MapSyncMod.getMod;

public class ModGui extends Screen {
	final Screen parentScreen;

	ServerConfig serverConfig = getMod().getServerConfig();

	int btnWidth = 150;
	int innerWidth = 500;
	int left = width / 2 - innerWidth / 2;
	int right = width / 2 + innerWidth / 2;
	int top = height / 3;

	EditBox syncServerAddressField;

	protected ModGui(Screen parentScreen) {
		super(new TextComponent("Map-Sync"));
		this.parentScreen = parentScreen;
	}

	@Override
	protected void init() {
		addRenderableWidget(new Button(
				right - 100,
				top,
				100, 20,
				new TextComponent("Close"),
				button -> minecraft.setScreen(parentScreen)));

		if (serverConfig != null) {
			addWidget(syncServerAddressField = new EditBox(font,
					left,
					top + 40,
					innerWidth - btnWidth, 20,
					new TextComponent("Sync Server Address")));
			syncServerAddressField.setMaxLength(256);
			syncServerAddressField.setValue(Optional.ofNullable(
					serverConfig.getSyncServerAddress()).orElse(""));

			addRenderableWidget(new Button(
					right - btnWidth,
					top + 40,
					btnWidth, 20,
					new TextComponent("Connect"),
					this::connectClicked));
		}
	}

	public void connectClicked(Button btn) {
		serverConfig.setSyncServerAddress(
				syncServerAddressField.getValue());
		getMod().shutDownSyncClient();
		getMod().getSyncClient();
		btn.active = false;
		btn.setMessage(new TextComponent("Connecting..."));
	}

	@Override
	public void render(@NotNull PoseStack poseStack, int i, int j, float f) {
		drawCenteredString(poseStack, font, title, width / 2, top, 0xFFFFFF);
		syncServerAddressField.render(poseStack, i, j, f);
		super.render(poseStack, i, j, f);
	}
}
