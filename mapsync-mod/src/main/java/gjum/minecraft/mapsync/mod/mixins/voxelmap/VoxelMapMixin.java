package gjum.minecraft.mapsync.mod.mixins.voxelmap;

import com.mamiyaotaru.voxelmap.VoxelMap;
import gjum.minecraft.mapsync.mod.integrations.voxelmap.VoxelMapHelper;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VoxelMap.class)
public abstract class VoxelMapMixin {
	@Inject(
		method = "lateInit",
		at = @At("TAIL"),
		remap = false
	)
	protected void mapsync$lateInit(
		final boolean showUnderMenus,
		final boolean isFair,
		final @NotNull CallbackInfo ci
	) {
		VoxelMapHelper.isModAvailable = true;
	}
}
