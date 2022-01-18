package gjum.minecraft.civ.mapsync.common.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static gjum.minecraft.civ.mapsync.common.MapSyncMod.getMod;
import static gjum.minecraft.civ.mapsync.common.Utils.printErrorRateLimited;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {
	@Inject(method = "handleLogin", at = @At("RETURN"))
	protected void onHandleLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
		if (!Minecraft.getInstance().isSameThread()) return; // will be called again on mc thread in a moment
		try {
			getMod().handleConnectedToServer(packet);
		} catch (Throwable e) {
			printErrorRateLimited(e);
		}
	}

	@Inject(method = "handleRespawn", at = @At("RETURN"))
	protected void onHandleRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
		if (!Minecraft.getInstance().isSameThread()) return; // will be called again on mc thread in a moment
		try {
			getMod().handleRespawn(packet);
		} catch (Throwable e) {
			printErrorRateLimited(e);
		}
	}

	@Inject(method = "handleLevelChunkWithLight", at = @At("RETURN"))
	protected void onHandleLevelChunkWithLight(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
		if (!Minecraft.getInstance().isSameThread()) return; // will be called again on mc thread in a moment
		try {
			getMod().handleMcChunk(packet);
		} catch (Throwable e) {
			printErrorRateLimited(e);
		}
	}
}
