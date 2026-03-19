package gjum.minecraft.mapsync.common.mixins.voxelmap;

import com.mamiyaotaru.voxelmap.persistent.CachedRegion;
import com.mamiyaotaru.voxelmap.persistent.PersistentMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PersistentMap.class)
public interface PersistentMapAccessor {
	@Accessor("world")
	public @Nullable ClientLevel mapsync$getWorld();

	@Accessor(
		value = "cachedRegions",
		remap = false
	)
	public @NotNull ConcurrentHashMap<String, CachedRegion> mapsync$getCachedRegions();

	@Accessor(
		value = "cachedRegionsPool",
		remap = false
	)
	public @NotNull List<CachedRegion> mapsync$getCachedRegionsPool();
}
