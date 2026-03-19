package gjum.minecraft.mapsync.common;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

public final class MixinManager implements IMixinConfigPlugin {
	private String mixinPackage = "";
	private boolean isVoxelMapLoaded;

	@Override
	public void onLoad(
		final @NotNull String mixinPackage
	) {
		this.mixinPackage = mixinPackage;
		this.isVoxelMapLoaded = hasClass("com.mamiyaotaru.voxelmap.VoxelConstants");
	}

	/// This could be replaced with a [FabricLoader#isModLoaded(String)] if this was a fabric-only mod but alas, no.
	private static boolean hasClass(
		final @NotNull String className
	) {
		try {
			MixinService.getService().getClassProvider().findClass(className, false);
			return true;
		}
		catch (final ClassNotFoundException e) {
			return false;
		}
	}

	@Override
	public boolean shouldApplyMixin(
		final @NotNull String targetClassName,
		final @NotNull String mixinClassName
	) {
		if (mixinClassName.startsWith(this.mixinPackage + ".voxelmap.")) {
			return this.isVoxelMapLoaded;
		}
		return true;
	}

	@Override
	public @Nullable String getRefMapperConfig() {
		return null;
	}

	@Override
	public void acceptTargets(
		final @NotNull Set<@NotNull String> myTargets,
		final @NotNull Set<@NotNull String> otherTargets
	) {

	}

	@Override
	public @Nullable List<@NotNull String> getMixins() {
		return null;
	}

	@Override
	public void preApply(
		final @NotNull String targetClassName,
		final @NotNull ClassNode targetClass,
		final @NotNull String mixinClassName,
		final @NotNull IMixinInfo mixinInfo
	) {

	}

	@Override
	public void postApply(
		final @NotNull String targetClassName,
		final @NotNull ClassNode targetClass,
		final @NotNull String mixinClassName,
		final @NotNull IMixinInfo mixinInfo
	) {

	}
}
