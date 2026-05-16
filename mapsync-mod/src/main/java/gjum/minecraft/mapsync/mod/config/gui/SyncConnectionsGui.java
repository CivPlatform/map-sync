package gjum.minecraft.mapsync.mod.config.gui;

import gjum.minecraft.mapsync.mod.sync.DimensionState;
import gjum.minecraft.mapsync.mod.net.SyncClient;
import gjum.minecraft.mapsync.mod.sync.GameContext;
import gjum.minecraft.mapsync.mod.sync.XaeroBackfillStatus;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
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
			Checkbox.builder(Component.literal("Auto-connect"), this.font)
				.pos(offsetRight - 100, this.offsetTop + 18)
				.selected(this.gameContext.getGameConfig().shouldAutoConnect())
				.onValueChange((checkbox, value) -> {
					this.gameContext.getGameConfig().setAutoConnect(value);
					this.gameContext.getGameConfig().save();
				})
				.build()
		);

		this.addRenderableWidget(
			Checkbox.builder(Component.literal("Preserve existing map data"), this.font)
				.pos(this.offsetLeft, this.offsetTop + 70)
				.selected(this.gameContext.getGameConfig().shouldPreserveExistingMapData())
				.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
					"When on, MapSync will not overwrite local map data for chunks "
						+ "it has never seen on this server. Empty areas the sync "
						+ "server knows about are still filled in. Granularity is "
						+ "per-region (32x32 chunks) for Xaero; JourneyMap and "
						+ "VoxelMap are fully protected until per-mod probes land."
				)))
				.onValueChange((checkbox, value) -> {
					this.gameContext.getGameConfig().setPreserveExistingMapData(value);
					this.gameContext.getGameConfig().save();
				})
				.build()
		);

		this.addRenderableWidget(
			Button
				.builder(
					Component.literal("Connect"),
					(button) -> {
						this.gameContext.getGameConfig().setSyncServerAddresses(Arrays.asList(
							StringUtils.split(this.addressFieldValue, ',')
						));
						this.gameContext.getGameConfig().save();
						this.gameContext.getSyncConnections().setAll(Set.copyOf(
							this.gameContext.getGameConfig().getSyncServerAddresses()
						));
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
	public void extractRenderState(
		final @NotNull GuiGraphicsExtractor guiGraphics,
		final int mouseX,
		final int mouseY,
		final float partialTick
	) {
		super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

		int top = this.offsetTop;
		guiGraphics.centeredText(this.font, this.title, this.width / 2, top, 0xFF_FF_FF);

		top += 70;
		if (this.gameContext.getDimensionState().orElse(null) instanceof final DimensionState dimensionState) {
			guiGraphics.text(
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
			top += 10;

			guiGraphics.text(
				this.font,
				"Tracked chunks: %,d across %,d regions".formatted(
					dimensionState.getTrackedChunkCount(),
					dimensionState.getLoadedRegionCount()
				),
				this.offsetLeft,
				top,
				0x88_88_88
			);
			top += 10;

			final XaeroBackfillStatus backfill = dimensionState.getBackfillStatus();
			final String backfillLine;
			final int backfillColor;
			switch (backfill) {
				case final XaeroBackfillStatus.WaitingForXaero $ -> {
					backfillLine = "Xaero backfill: waiting for region detection...";
					backfillColor = 0xFFffcc66;
				}
				case final XaeroBackfillStatus.Backfilling b -> {
					backfillLine = "Xaero backfill: seeding %,d regions...".formatted(b.regionsDone());
					backfillColor = 0xFFffcc66;
				}
				case final XaeroBackfillStatus.Completed c -> {
					backfillLine = "Xaero backfill: done (%,d regions seeded)".formatted(c.regionsDone());
					backfillColor = 0xFF88ff88;
				}
				case final XaeroBackfillStatus.Failed f -> {
					backfillLine = "Xaero backfill: failed (" + f.reason() + ")";
					backfillColor = 0xFFff8888;
				}
				case final XaeroBackfillStatus.NotNeeded n -> {
					backfillLine = "Xaero backfill: skipped (" + n.reason() + ")";
					backfillColor = 0x88_88_88;
				}
			}
			guiGraphics.text(this.font, backfillLine, this.offsetLeft, top, backfillColor);
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
			guiGraphics.text(this.font, statusText, this.offsetLeft, top, statusColor);
			top += 10;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.gameContext.getGameConfig().save();
		this.minecraft.setScreen(this.parentScreen);
	}
}
