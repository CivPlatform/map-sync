package gjum.minecraft.civ.mapsync.fabric;

import gjum.minecraft.civ.mapsync.common.MapSyncMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

public class FabricMapSyncMod extends MapSyncMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		init();
	}

	@Override
	public void registerKeyBinding(KeyMapping mapping) {
		KeyBindingHelper.registerKeyBinding(mapping);
	}
}
