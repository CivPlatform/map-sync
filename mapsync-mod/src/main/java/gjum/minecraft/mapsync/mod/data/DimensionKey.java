package gjum.minecraft.mapsync.mod.data;

import java.util.HexFormat;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.Strings;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

/// DimensionKeys are a MapSync dimension identifier (eg: `minecraft:overworld#FFAABB`) that represents a dimension
/// identifier and its seed. It is presumed that each dimension, even if it shares the same identifier, will have a
/// unique seed. For example, two shards of a server may both have `minecraft:overworld`, but presumably have different
/// seeds by virtue of each shard being a separate Minecraft server.
///
/// For backwards compatibility purposes, DimensionKeys are strings to avoid the need for database migrations. They are
/// also case-insensitive. Seedless DimensionKeys are permitted, though discouraged, and will eagerly match any
/// DimensionKey that shares its identifier (eg: `minecraft:overworld` will match with `minecraft:overworld#FFAABB`).
public record DimensionKey(
	@NotNull String internal
) {
	public DimensionKey(
		final Level level
	) {
		this(fromLevel(level));
	}

	@Override
	public @NonNull String toString() {
		return this.internal();
	}

	@Override
	public boolean equals(
		final Object obj
	) {
		if (obj == null) {
			return false;
		}
		if (obj == this) {
			return true;
		}
		if (!(obj instanceof final DimensionKey other)) {
			return false;
		}
		return matches(
			this.internal(),
			other.internal()
		);
	}

	public interface IMixin {
		public long mapsync$getSeed();
	}

	private static @NotNull String fromLevel(
		final Level level
	) {
		if (level == null) {
			return "UNKNOWN";
		}
		final var levelAccessor = (IMixin) level;
		return "%s#%s".formatted(
			level.dimension().identifier().toString(),
			HexFormat.of().toHexDigits(levelAccessor.mapsync$getSeed())
		);
	}

	@Contract("null, _ -> false")
	public static boolean matches(
		final Level level,
		final @NotNull DimensionKey key
	) {
		if (level == null) {
			return false;
		}
		return matches(
			key.internal(),
			fromLevel(level)
		);
	}

	private static boolean matches(
		@NotNull String lhs,
		@NotNull String rhs
	) {
		final int
			lhsSeedIndex = lhs.indexOf('#'),
			rhsSeedIndex = rhs.indexOf('#');
		if (lhsSeedIndex >= 0 ^ rhsSeedIndex >= 0) {
			if (lhsSeedIndex >= 0) {
				lhs = lhs.substring(0, lhsSeedIndex);
			}
			if (rhsSeedIndex >= 0) {
				rhs = rhs.substring(0, rhsSeedIndex);
			}
		}
		return Strings.CI.equals(lhs, rhs);
	}
}
