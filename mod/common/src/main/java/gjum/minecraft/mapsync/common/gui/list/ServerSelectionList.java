package gjum.minecraft.mapsync.common.gui.list;

import com.mojang.blaze3d.vertex.PoseStack;
import gjum.minecraft.mapsync.common.MapSyncMod;
import gjum.minecraft.mapsync.common.net.SyncClient;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import org.jetbrains.annotations.NotNull;

@Environment(EnvType.CLIENT)
class ServerSelectionList extends ObjectSelectionList<ServerSelectionList.Entry> {
    private final ServerListScreen parentScreen;

    ServerSelectionList(
            final @NotNull ServerListScreen parentScreen,
            final @NotNull Minecraft minecraft,
            final int width,
            final int height,
            final int topY,
            final int bottomY,
            final int rowHeight
    ) {
        super(
                Objects.requireNonNull(minecraft),
                width,
                height,
                topY,
                bottomY,
                rowHeight
        );
        this.parentScreen = Objects.requireNonNull(parentScreen);
    }

    @Override
    public void setSelected(
            final Entry entry
    ) {
        super.setSelected(entry);
        this.parentScreen.onSelectionChange();
    }

    void updateServerList() {
        clearEntries();

        for (final SyncClient syncClient : MapSyncMod.getMod().getSyncClients()) {
            addEntry(new Entry(syncClient));
        }
    }

    @Environment(EnvType.CLIENT)
    protected class Entry extends ObjectSelectionList.Entry<Entry> {
        public static final int ROW_HEIGHT = 36;

        public final SyncClient syncClient;

        public Entry(
                final @NotNull SyncClient syncClient
        ) {
            this.syncClient = Objects.requireNonNull(syncClient);
        }

        @Override
        public void render(
                @NotNull PoseStack poseStack,
                int entryIndex,
                int entryY,
                int entryX,
                int listWidth,
                int entryHeight,
                int mouseX,
                int mouseY,
                boolean isMouseOver,
                float partialTick
        ) {
            final Font font = ServerSelectionList.this.minecraft.font;

            drawString(
                    poseStack,
                    font,
                    this.syncClient.address,
                    entryX + 2,
                    entryY + 2,
                    0x888888
            );

            final String statusText; final int statusColour; statusGetter: {
                // TODO: Need a much better way of ascertaining the client's state
                if (this.syncClient.isEncrypted()) {
                    statusText = "Connected";
                    statusColour = 0x008800;
                    break statusGetter;
                }
                final String error = this.syncClient.getError();
                if (error != null) {
                    if (error.startsWith("Connection refused:")) {
                        statusText = "Connection refused";
                    }
                    else if ("Connection reset by peer".equals(error)) {
                        statusText = "Kicked";
                    }
                    else {
                        statusText = error;
                    }
                    statusColour = 0xff8888;
                    break statusGetter;
                }
                statusText = "Connecting...";
                statusColour = 0xffffff;
            }
            drawString(
                    poseStack,
                    font,
                    statusText, // TODO: See about truncating the string if it's too wide
                    entryX + 2,
                    entryY + entryHeight - font.lineHeight - 2,
                    statusColour
            );
        }

        @Override
        public boolean mouseClicked(
                final double x,
                final double y,
                final int mouseButton
        ) {
            ServerSelectionList.this.setSelected(this);
            return false; // presumably interpreted as "hadError"
        }

        @Override
        public @NotNull Component getNarration() {
            return TextComponent.EMPTY;
        }
    }
}
