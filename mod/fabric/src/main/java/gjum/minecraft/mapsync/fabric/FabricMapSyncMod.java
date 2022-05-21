package gjum.minecraft.mapsync.fabric;

import gjum.minecraft.mapsync.common.MapSyncMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

public class FabricMapSyncMod extends MapSyncMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		init();
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			try {
				handleTick();
			} catch (Throwable e) {
				e.printStackTrace();
			}
		});
	}

	@Override
	public String getVersion() {
		return VERSION + "+fabric";
	}

	@Override
	public void registerKeyBinding(KeyMapping mapping) {
		KeyBindingHelper.registerKeyBinding(mapping);
	}
}
