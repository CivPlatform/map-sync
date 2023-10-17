package gjum.minecraft.mapsync.common.gui.add;

import com.mojang.blaze3d.vertex.PoseStack;
import gjum.minecraft.mapsync.common.MapSyncMod;
import gjum.minecraft.mapsync.common.config.ServerConfig;
import gjum.minecraft.mapsync.common.gui.list.ServerListScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@Environment(EnvType.CLIENT)
public class AddServerScreen extends Screen {
    private final ServerListScreen previousScreen;

    private boolean alreadySetup;
    private EditBox addressInput;

    public AddServerScreen(
            final @NotNull ServerListScreen previousScreen
    ) {
        super(new TextComponent("Add a MapSync Server"));
        this.previousScreen = Objects.requireNonNull(previousScreen);
    }

    @Override
    protected void init() {
        super.init();

        if (this.alreadySetup) {
            this.addressInput.setX(this.width / 2 - 100);
        }
        else {
            this.alreadySetup = true;
            this.addressInput = new EditBox(
                    this.font,
                    this.width / 2 - 100,
                    116,
                    200,
                    20,
                    new TranslatableComponent("addServer.enterIp")
            );
        }

        addRenderableWidget(this.addressInput);

        this.addressInput.setMaxLength(128);
        this.addressInput.setFocus(true);
        setInitialFocus(this.addressInput);

        // Add button
        addRenderableWidget(new Button(
                this.width / 2 - 100,
                this.height / 4 + 96 + 12,
                200,
                20,
                new TextComponent("Add Server"),
                (button) -> {
                    final String serverAddress = this.addressInput.getValue();
                    if (StringUtils.isBlank(serverAddress)) {
                        return;
                    }

                    final ServerConfig config = MapSyncMod.getMod().getServerConfig();
                    if (config == null) {
                        // TODO: Add error log
                        return;
                    }

                    final List<String> serverAddresses = new ArrayList<>(config.getSyncServerAddresses());
                    if (serverAddresses.contains(serverAddress)) {
                        return;
                    }
                    serverAddresses.add(serverAddress);
                    config.setSyncServerAddresses(serverAddresses);

                    // TODO: Need to do this to force a refresh and for each entry to be associated with a SyncClient
                    //       otherwise we'd need to do a find, since there's no way to match an address to a client
                    //       without iterating, which is error prone
                    this.previousScreen.refresh();
                    this.minecraft.setScreen(this.previousScreen);
                }
        ));

        // Cancel button
        addRenderableWidget(new Button(
                this.width / 2 - 100,
                this.height / 4 + 120 + 12,
                200,
                20,
                CommonComponents.GUI_CANCEL,
                (button) -> this.minecraft.setScreen(this.previousScreen)
        ));
    }

    @Override
    public void tick() {
        super.tick();
        this.addressInput.tick();
    }

    @Override
    public void render(
            final @NotNull PoseStack poseStack,
            final int mouseX,
            final int mouseY,
            final float partialTicks
    ) {
        renderBackground(poseStack);

        drawCenteredString(
                poseStack,
                this.font,
                this.title,
                this.width / 2,
                20,
                16777215
        );

        drawString(
                poseStack,
                this.font,
                new TranslatableComponent("addServer.enterIp"),
                this.width / 2 - 100,
                100,
                10526880
        );

        super.render(poseStack, mouseX, mouseY, partialTicks);
    }
}
