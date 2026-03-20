package gjum.minecraft.mapsync.mod;

import static gjum.minecraft.mapsync.mod.MapSyncMod.getMod;

import gjum.minecraft.mapsync.mod.config.ServerConfig;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class ModGui extends Screen {
	final Screen parentScreen;

	ServerConfig serverConfig = getMod().getServerConfig();

	int innerWidth = 300;
	int left;
	int right;
	int top;

	int centerX = width / 2;
	int centerY = width / 2;

	EditBox syncServerAddressField;
	Button syncServerConnectBtn;
	Button syncServerDisconnectBtn;
	Button syncServerPurgeBtn;

	public ModGui(Screen parentScreen) {
		super(Component.literal("Map-Sync"));
		this.parentScreen = parentScreen;
	}

	@Override
	public void resize(int width, int height) {
		super.resize(width, height);
		init();
	}

	@Override
	protected void init() {
		try {
			left = width / 2 - innerWidth / 2;
			right = width / 2 + innerWidth / 2;
			top = height / 3;

			centerX = width / 2;
			centerY = width / 2;

			int buttonWidth = 100;
			int buttonHeight = 20;

			clearWidgets();

			addRenderableWidget(
				Button.builder(Component.literal("Close"), (button) -> minecraft.setScreen(parentScreen))
					.bounds(centerX - (buttonWidth / 2), centerY, buttonWidth, buttonHeight)
					.build()
			);

			if (serverConfig != null) {
				addWidget(syncServerAddressField = new EditBox(font,
						left,
						top + 40,
						innerWidth - 110, 20,
						Component.literal("Sync Server Address")));
				syncServerAddressField.setMaxLength(256);
				syncServerAddressField.setValue(String.join(" ",
						serverConfig.getSyncServerAddresses()));

				addRenderableWidget(
					syncServerConnectBtn = Button.builder(Component.literal("Connect"), this::connectClicked)
						.bounds(right - 100, top + 40, 100, 20)
						.build()
				);

				addRenderableWidget(
					syncServerDisconnectBtn = Button.builder(Component.literal("Disconnect"), this::disconnectClicked)
					.bounds(right - 100, syncServerAddressField.getY() + 25, 100, 20)
					.build()
				);

				addRenderableWidget(
					syncServerPurgeBtn = Button.builder(Component.literal("Purge"), this::purgeClicked)
						.bounds(width - 60, top + 40, 60, 20)
						.bounds(10, height - 30, 60, 20)
						.build()
				);
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public void connectClicked(Button btn) {
		try {
			if (syncServerAddressField == null) return;
			var addresses = List.of(syncServerAddressField.getValue().split("[^-_.:A-Za-z0-9]+"));
			serverConfig.setSyncServerAddresses(addresses);
			getMod().shutDownSyncClients();
			getMod().getSyncClients();
			btn.active = false;
			syncServerDisconnectBtn.active = true;
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	// TODO: not working
	public void disconnectClicked(Button btn) {
		if (syncServerAddressField == null) return;
		getMod().shutDownSyncClients();
		btn.active = false;
	}

	public void purgeClicked(Button btn) {
		DimensionState dimState = getMod().getDimensionState();
		if (dimState != null) {
			dimState.PurgeRegionTimeStamps();
		}
	}

	@Override
	public void render(@NotNull GuiGraphics guiGraphics, int i, int j, float f) {
		
		try {
			// wait for init() to finish
			if (syncServerAddressField == null) return;
			if (syncServerConnectBtn == null) return;
			super.render(guiGraphics, i, j, f);

			guiGraphics.drawCenteredString(font, title, centerX, top, 0xFFFFFFFF);
			syncServerAddressField.render(guiGraphics, i, j, f);

			var dimensionState = getMod().getDimensionState();
			if (dimensionState != null) {
				String counterText = String.format(
						"In dimension %s, received %d chunks, rendered %d, rendering %d",
						dimensionState.dimension.identifier(),
						dimensionState.getNumChunksReceived(),
						dimensionState.getNumChunksRendered(),
						dimensionState.getRenderQueueSize()
				);
				guiGraphics.drawCenteredString(font, counterText, centerX, syncServerAddressField.getY() - 20, 0xFF888888);
			}

			int msgY = syncServerAddressField.getY() + 25;
			var syncClients = getMod().getSyncClients();
			for (var client : syncClients) {
				int statusColor;
				String statusText;
				if (client.isEncrypted()) {
					statusColor = 0xFF008800;
					statusText = "Connected";
				} else if (client.getError() != null) {
					statusColor = 0xFFff8888;
					statusText = client.getError();
				} else {
					statusColor = 0xFFffffff;
					statusText = "Connecting...";
				}
				statusText = client.address + "  " + statusText;
				guiGraphics.drawString(font, statusText, left, msgY, statusColor);
				msgY += 10;
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
}
