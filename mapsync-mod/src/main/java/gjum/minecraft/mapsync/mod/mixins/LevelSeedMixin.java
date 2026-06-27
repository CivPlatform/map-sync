package gjum.minecraft.mapsync.mod.mixins;

import gjum.minecraft.mapsync.mod.data.DimensionKey;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
public class LevelSeedMixin implements DimensionKey.IMixin {
	@Unique
	private long seed;

	@Inject(
		method = "<init>",
		at = @At("TAIL")
	)
	private void mapsync$setSeedInConstructor(
		final WritableLevelData levelData,
		final ResourceKey<Level> dimension,
		final RegistryAccess registryAccess,
		final Holder<DimensionType> dimensionTypeRegistration,
		final boolean isClientSide,
		final boolean isDebug,
		final long biomeZoomSeed,
		final int maxChainedNeighborUpdates,
		final @NotNull CallbackInfo ci
	) {
		this.seed = biomeZoomSeed;
	}

	@Override
	public long mapsync$getSeed() {
		return this.seed;
	}
}
