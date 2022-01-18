package gjum.minecraft.civ.mapsync.forge;

import gjum.minecraft.civ.mapsync.common.MapSyncMod;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("civmapsync")
public class ForgeMapSyncMod extends MapSyncMod {
	public ForgeMapSyncMod() {
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
		MinecraftForge.EVENT_BUS.register(this);
	}

	public void clientSetup(FMLClientSetupEvent event) {
		init();
	}

	@Override
	public void registerKeyBinding(KeyMapping mapping) {
		ClientRegistry.registerKeyBinding(mapping);
	}
}
