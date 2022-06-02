package gjum.minecraft.mapsync.common;

import com.mojang.blaze3d.vertex.PoseStack;
import gjum.minecraft.mapsync.common.config.ServerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static gjum.minecraft.mapsync.common.MapSyncMod.getMod;

public class ModGui extends Screen {
	final Screen parentScreen;

	ServerConfig serverConfig = getMod().getServerConfig();

	int innerWidth = 300;
	int left;
	int right;
	int top;

	EditBox syncServerAddressField;
	Button syncServerConnectBtn;

	protected ModGui(Screen parentScreen) {
		super(new TextComponent("Map-Sync"));
		this.parentScreen = parentScreen;
	}

	@Override
	public void resize(Minecraft mc, int width, int height) {
		super.resize(mc, width, height);
		init();
	}

	@Override
	protected void init() {
		left = width / 2 - innerWidth / 2;
		right = width / 2 + innerWidth / 2;
		top = height / 3;

		clearWidgets();

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
					innerWidth - 110, 20,
					new TextComponent("Sync Server Address")));
			syncServerAddressField.setMaxLength(256);
			syncServerAddressField.setValue(String.join(" ",
					serverConfig.getSyncServerAddresses()));

			addRenderableWidget(syncServerConnectBtn = new Button(
					right - 100,
					top + 40,
					100, 20,
					new TextComponent("Connect"),
					this::connectClicked));
		}
	}

	public void connectClicked(Button btn) {
		var addresses = List.of(syncServerAddressField.getValue().split("[^-_.:A-Za-z0-9]+"));
		serverConfig.setSyncServerAddresses(addresses);
		getMod().shutDownSyncClients();
		getMod().getSyncClients();
		btn.active = false;
	}

	@Override
	public void render(@NotNull PoseStack poseStack, int i, int j, float f) {
		// wait for init() to finish
		if (syncServerAddressField == null) return;
		if (syncServerConnectBtn == null) return;

		renderBackground(poseStack);
		drawCenteredString(poseStack, font, title, width / 2, top, 0xFFFFFF);
		syncServerAddressField.render(poseStack, i, j, f);

		var dimensionState = getMod().getDimensionState();
		if (dimensionState != null) {
			String counterText = String.format(
					"In dimension %s, received %d chunks, rendered %d, rendering %d",
					dimensionState.dimension.location(),
					dimensionState.getNumChunksReceived(),
					dimensionState.getNumChunksRendered(),
					dimensionState.getRenderQueueSize()
			);
			drawString(poseStack, font, counterText, left, top + 70, 0x888888);
		}

		int numConnected = 0;
		int msgY = top + 90;
		var syncClients = getMod().getSyncClients();
		for (var client : syncClients) {
			int statusColor;
			String statusText;
			if (client.isEncrypted()) {
				numConnected++;
				statusColor = 0x008800;
				statusText = "Connected";
			} else if (client.getError() != null) {
				statusColor = 0xff8888;
				statusText = client.getError();
			} else {
				statusColor = 0xffffff;
				statusText = "Connecting...";
			}
			statusText = client.address + "  " + statusText;
			drawString(poseStack, font, statusText, left, msgY, statusColor);
			msgY += 10;
		}

		super.render(poseStack, i, j, f);
	}
}
