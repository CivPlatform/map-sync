package gjum.minecraft.mapsync.mod.utils;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/// This is intended as a remedial gap-filler class to cover for the inadequacies of Java's type system. It includes its
/// own exception class to ensure the exception is generic enough to apply: [IllegalArgumentException] is not relevant
/// for assertions during packet \[de\]serialisation.
public final class Assertions {
	public static void assertNotNull(
		final @NotNull String name,
		final Object value
	) {
		Objects.requireNonNull(name);
		if (value == null) {
			throw new AssertionException("'%s' is null!".formatted(
				name
			));
		}
	}

	public static void assertLength(
		final @NotNull String name,
		final int currentLength,
		final int requiredLength
	) {
		Objects.requireNonNull(name);
		if (currentLength != requiredLength) {
			throw new AssertionException("'%s' has length %d when it must be %d".formatted(
				name,
				currentLength,
				requiredLength
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
