package gjum.minecraft.mapsync.common.gui.list;

import com.mojang.blaze3d.vertex.PoseStack;
import gjum.minecraft.mapsync.common.MapSyncMod;
import gjum.minecraft.mapsync.common.config.ServerConfig;
import gjum.minecraft.mapsync.common.gui.add.AddServerScreen;
import gjum.minecraft.mapsync.common.gui.edit.EditServerScreen;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ServerListScreen extends Screen {
	private Screen previousScreen;

	private boolean alreadySetup;
	private ServerSelectionList serverSelectionList;
	private Button editButton;
	private Button deleteButton;

	public ServerListScreen(
			final Screen previousScreen
	) {
		super(new TextComponent("MapSync Servers"));
		this.previousScreen = previousScreen;
	}

	public @Nullable Screen getPreviousScreen() {
		return this.previousScreen;
	}

	public void refresh() {
		if (this.alreadySetup) {
			this.serverSelectionList.updateServerList();
		}
	}

	@Override
	protected void init() {
		super.init();

		if (this.alreadySetup) {
			this.serverSelectionList.updateSize(
					this.width,
					this.height,
					32,
					this.height - 36
			);
		}
		else {
			this.alreadySetup = true;
			this.serverSelectionList = new ServerSelectionList(
					this,
					this.minecraft,
					this.width,
					this.height,
					32,
					this.height - 36,
					ServerSelectionList.Entry.ROW_HEIGHT
			);
			this.serverSelectionList.updateServerList();
		}

		addRenderableWidget(this.serverSelectionList);

		// Edit button
		this.editButton = addRenderableWidget(new Button(
				this.width / 2 - 154,
				this.height - 28,
				70,
				20,
				new TranslatableComponent("selectServer.edit"),
				(button) -> {
					final ServerSelectionList.Entry selected = this.serverSelectionList.getSelected();
					if (selected == null) {
						// TODO: Add error log
						return;
					}

					this.minecraft.setScreen(new EditServerScreen(this, selected.syncClient.address));
				}
		));

		// Delete button
		this.deleteButton = addRenderableWidget(new Button(
				this.width / 2 - 74,
				this.height - 28,
				70,
				20,
				new TranslatableComponent("selectServer.delete"),
				(button) -> {
					final ServerSelectionList.Entry selected = this.serverSelectionList.getSelected();
					if (selected == null) {
						// TODO: Add error log
						return;
					}
					final ServerConfig config = MapSyncMod.getMod().getServerConfig();
					if (config == null) {
						// TODO: Add error log
						return;
					}
					this.minecraft.setScreen(new ConfirmScreen(
							(confirmed) -> {
								if (confirmed) {
									final List<String> serverAddresses = new ArrayList<>(config.getSyncServerAddresses());
									serverAddresses.removeIf((serverAddress) -> StringUtils.equals(serverAddress, selected.syncClient.address)); // remove duplicates too
									config.setSyncServerAddresses(serverAddresses);

									selected.syncClient.shutDown();

									this.serverSelectionList.setSelected(null);
									this.serverSelectionList.updateServerList();
								}
								this.minecraft.setScreen(this);
							},
							new TranslatableComponent("selectServer.deleteQuestion"),
							new TranslatableComponent("selectServer.deleteWarning", selected.syncClient.address),
							new TranslatableComponent("selectServer.deleteButton"),
							CommonComponents.GUI_BACK
					));
				}
		));

		// Refresh button
		addRenderableWidget(new Button(
				this.width / 2 + 4,
				this.height - 28,
				70,
				20,
				new TranslatableComponent("selectServer.refresh"),
				(button) -> {
					this.serverSelectionList.updateServerList();
					onSelectionChange();
				}
		));

		// Add button
		addRenderableWidget(new Button(
				this.width / 2 + 4 + 76,
				this.height - 28,
				75,
				20,
				new TranslatableComponent("selectServer.add"),
				(button) -> this.minecraft.setScreen(new AddServerScreen(this))
		));

		onSelectionChange();
	}

	@Override
	public void render(
			final @NotNull PoseStack poseStack,
			final int mouseX,
			final int mouseY,
			final float partialTicks
	) {
		renderDirtBackground(255);

		super.render(poseStack, mouseX, mouseY, partialTicks);

		drawCenteredString(
				poseStack,
				this.font,
				"MapSync Servers",
				this.width / 2,
				15,
				0x888888
		);
	}

	void onSelectionChange() {
		this.editButton.active = false;
		this.deleteButton.active = false;
		final ServerSelectionList.Entry selected = this.serverSelectionList.getSelected();
		if (selected != null) {
			this.editButton.active = true;
			this.deleteButton.active = true;
		}
	}
}
