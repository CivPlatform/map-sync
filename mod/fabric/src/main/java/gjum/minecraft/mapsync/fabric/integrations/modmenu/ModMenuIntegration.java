package gjum.minecraft.mapsync.fabric.integrations.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import gjum.minecraft.mapsync.common.ModGui;
import org.jetbrains.annotations.NotNull;

/**
 * Adds support for https://github.com/TerraformersMC/ModMenu (Fabric only)
 */
public class ModMenuIntegration implements ModMenuApi {
    public @NotNull ConfigScreenFactory<ModGui> getModConfigScreenFactory() {
        return ModGui::new;
    }
}
