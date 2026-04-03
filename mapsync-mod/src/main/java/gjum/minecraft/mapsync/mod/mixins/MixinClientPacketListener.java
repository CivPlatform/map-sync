package gjum.minecraft.mapsync.mod.mixins;

import static gjum.minecraft.mapsync.mod.MapSyncMod.getMod;
import static gjum.minecraft.mapsync.mod.Utils.printErrorRateLimited;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {

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
			getMod().handleMcFullChunk(packet.getX(), packet.getZ());
		} catch (Throwable e) {
			printErrorRateLimited(e);
		}
	}


	@Inject(method = "handleBlockUpdate", at = @At("RETURN"))
	protected void onHandleBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
		if (!Minecraft.getInstance().isSameThread()) return; // will be called again on mc thread in a moment
		try {
			BlockPos pos = packet.getPos();
			getMod().handleMcChunkPartialChange(pos.getX() >> 4, pos.getZ() >> 4);
		} catch (Throwable e) {
			printErrorRateLimited(e);
		}
	}

	@Inject(method = "handleBlockDestruction", at = @At("RETURN"))
	protected void onHandleBlockDestruction(ClientboundBlockDestructionPacket packet, CallbackInfo ci) {
		if (!Minecraft.getInstance().isSameThread()) return; // will be called again on mc thread in a moment
		try {
			BlockPos pos = packet.getPos();
			getMod().handleMcChunkPartialChange(pos.getX() >> 4, pos.getZ() >> 4);
		} catch (Throwable e) {
			printErrorRateLimited(e);
		}
	}
}
