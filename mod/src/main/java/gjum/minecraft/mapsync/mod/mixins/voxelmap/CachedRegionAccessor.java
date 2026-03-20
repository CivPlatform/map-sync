package gjum.minecraft.mapsync.mod.mixins.voxelmap;

import com.mamiyaotaru.voxelmap.persistent.CachedRegion;
import com.mamiyaotaru.voxelmap.persistent.CompressibleMapData;
import java.util.concurrent.locks.ReentrantLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnknownNullability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CachedRegion.class)
public interface CachedRegionAccessor {
	@Accessor(
		value = "data",
		remap = false
	)
	public @UnknownNullability CompressibleMapData mapsync$getData();

	@Accessor(
		value = "loaded",
		remap = false
	)
	public boolean mapsync$isLoaded();

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
