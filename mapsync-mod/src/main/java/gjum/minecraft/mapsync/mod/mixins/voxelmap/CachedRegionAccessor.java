package gjum.minecraft.mapsync.mod.mixins.voxelmap;

import com.mamiyaotaru.voxelmap.persistent.CachedRegion;
import java.util.concurrent.locks.ReentrantLock;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CachedRegion.class)
public interface CachedRegionAccessor {
	@Invoker(
		value = "load",
		remap = false
	)
	public void mapsync$load();

	@Accessor(
		value = "threadLock",
		remap = false
	)
	public @NotNull ReentrantLock mapsync$getThreadLock();

	@Accessor(
		value = "empty",
		remap = false
	)
	public void mapsync$setIsEmpty(
		boolean isEmpty
	);

	@Accessor(
		value = "liveChunksUpdated",
		remap = false
	)
	public void mapsync$setLiveChunksUpdated(
		boolean liveChunksUpdated
	);

	@Accessor(
		value = "dataUpdated",
		remap = false
	)
	public void mapsync$setDataUpdated(
		boolean dataUpdated
	);
}
