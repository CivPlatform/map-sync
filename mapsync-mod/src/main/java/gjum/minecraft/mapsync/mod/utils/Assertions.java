package gjum.minecraft.mapsync.mod.utils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.LongRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

/// This is intended as a remedial gap-filler class to cover for the inadequacies of Java's type system. It includes its
/// own exception class to ensure the exception is generic enough to apply: [IllegalArgumentException] is not relevant
/// for assertions during packet \[de\]serialisation.
public final class Assertions {
	public static void assertNotNull(
		final Object value
	) {
		if (value == null) {
			throw new AssertionException(" is null!");
		}
	}

	public static void assertLength(
		final int currentLength,
		final int requiredLength
	) {
		if (currentLength != requiredLength) {
			throw new AssertionException("length is %d when it must be %d".formatted(
				currentLength,
				requiredLength
			));
		}
	}

	/// Asserts whether a given value fits within the given mask. For example: `256` would not fit within a mask of
	/// `0xFF`, making this a good way to test the validity of unsigned integers in larger integer types.
	public static long assertMasked(
		final long mask,
		final long value
	) {
		if ((value & ~mask) != 0L) {
			throw new AssertionException("%d does not fit within mask %d".formatted(
				value,
				mask
			));
		}
		return value;
	}

	/// @return Returns an unmodifiable shallow-copy of the given map.
	public static <T> @NotNull @Unmodifiable List<@NotNull T> assertNonNullList(
		final List<T> map
	) {
		try {
			return List.copyOf(map);
		}
		catch (final NullPointerException thrown) {
			throw new AssertionException("list is null or contains nulls!");
		}
	}

	/// @return Returns an unmodifiable shallow-copy of the given map.
	public static <K, V> @NotNull @Unmodifiable Map<@NotNull K, @NotNull V> assertNonNullMap(
		final Map<K, V> map
	) {
		try {
			return Map.copyOf(map);
		}
		catch (final NullPointerException thrown) {
			throw new AssertionException("map is null or contains nulls!");
		}
	}

	public static void assertRange(
		final @NotNull LongRange range,
		final long value
	) {
		if (!range.contains(value)) {
			throw new AssertionException("%d is not within range %d..%d".formatted(
				value,
				range.getMinimum(),
				range.getMaximum()
			));
		}
	}
}

final class AssertionException extends RuntimeException {
	public AssertionException(
		final @NotNull String reason
	) {
		super(Objects.requireNonNull(reason));
	}
}
