package gjum.minecraft.mapsync.mod.config.gui;

import gjum.minecraft.mapsync.mod.sync.DimensionState;
import gjum.minecraft.mapsync.mod.net.SyncClient;
import gjum.minecraft.mapsync.mod.sync.GameContext;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

public final class SyncConnectionsGui extends Screen {
	private final Screen parentScreen;
	private final GameContext gameContext;
	private String addressFieldValue;

	public SyncConnectionsGui(
		final Screen parentScreen,
		final @NotNull GameContext gameContext
	) {
		super(Component.literal("MapSync"));
		this.parentScreen = parentScreen;
		this.gameContext = Objects.requireNonNull(gameContext);
		this.addressFieldValue = String.join(",", gameContext.getGameConfig().getSyncServerAddresses());
	}

	private volatile int offsetTop;
	private volatile int offsetLeft;

	@Override
	protected void init() {
		final int innerWidth = 300;
		this.offsetLeft = this.width / 2 - innerWidth / 2;
		final int offsetRight = this.width / 2 + innerWidth / 2;
		this.offsetTop = this.height / 3;

		this.addRenderableWidget(
			Button.builder(Component.literal("Close"), (button) -> this.minecraft.setScreen(this.parentScreen))
				.pos(offsetRight - 100, this.offsetTop)
				.width(100)
				.build()
		);

		final EditBox addressField = this.addRenderableWidget(new EditBox(
			this.font,
			this.offsetLeft,
			this.offsetTop + 40,
			innerWidth - 110,
			20,
			Component.literal("Sync Server Addresses")
		));
		addressField.setValue(this.addressFieldValue);
		addressField.setResponder((value) -> this.addressFieldValue = value);

		this.addRenderableWidget(
			Button
				.builder(
					Component.literal("Connect"),
					(button) -> {
						final List<String> syncAddresses = Stream.of(StringUtils.split(this.addressFieldValue, ','))
							.filter(StringUtils::isNotBlank)
							.distinct()
							.toList();
						this.gameContext.getGameConfig().setSyncServerAddresses(syncAddresses);
						this.gameContext.getSyncConnections().setAll(Set.copyOf(syncAddresses));
					}
				)
				.pos(offsetRight - 100, this.offsetTop + 40)
				.width(100)
				.build()
		);

		this.addRenderableWidget(
			Button
				.builder(
					CommonComponents.GUI_DISCONNECT,
					(button) -> this.gameContext.getSyncConnections().closeAll(true)
				)
				.pos(offsetRight - 100, this.offsetTop + 65)
				.width(100)
				.build()
		);

		this.addRenderableWidget(
			Button
				.builder(
					Component.literal("Purge"),
					(button) -> this.gameContext.getDimensionState().ifPresent(DimensionState::PurgeRegionTimeStamps)
				)
				.pos(10, this.height - 30)
				.width(60)
				.build()
		);

		addRenderableWidget(
			Button
				.builder(
					Component.literal("Close"),
					(button) -> this.onClose()
				)
				.pos((this.width / 2) - (100 / 2), this.height - 30)
				.width(100)
				.build()
		);
	}

	@Override
	public void render(
		final @NotNull GuiGraphics guiGraphics,
		final int mouseX,
		final int mouseY,
		final float partialTick
	) {
		super.render(guiGraphics, mouseX, mouseY, partialTick);

		int top = this.offsetTop;
		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, top, 0xFF_FF_FF);

		top += 70;
		if (this.gameContext.getDimensionState().orElse(null) instanceof final DimensionState dimensionState) {
			guiGraphics.drawString(
				this.font,
				"In dimension %s, received %d chunks, rendered %d, rendering %d".formatted(
					dimensionState.dimension.identifier(),
					dimensionState.getNumChunksReceived(),
					dimensionState.getNumChunksRendered(),
					dimensionState.getRenderQueueSize()
				),
				this.offsetLeft,
				top,
				0x88_88_88
			);
			top += 20;
		}

		for (final SyncClient client : this.gameContext.getSyncConnections()) {
			String statusText = client.syncAddress;
			final int statusColor;
			var connectionState = client.state();
			switch (connectionState) {
				case DISCONNECTED -> {
					statusColor = 0xFFff8888;
					statusText += " Disconnected";
				}
				case CONNECTED -> {
					statusColor = 0xFF8888ff;
					statusText += " Connected (not authed)";
				}
				case WELCOMED -> {
					statusColor = 0xFF88ff88;
					statusText += " Connected and authed";
				}
				default -> {
					statusColor = 0xFFFFFF00;
					statusText += " Unknown state: " + connectionState;
				}
			}
			guiGraphics.drawString(this.font, statusText, this.offsetLeft, top, statusColor);
			top += 10;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parentScreen);
	}
}
