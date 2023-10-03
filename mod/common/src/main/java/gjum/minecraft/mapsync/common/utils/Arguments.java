package gjum.minecraft.mapsync.common.utils;

import org.jetbrains.annotations.NotNull;

public final class Arguments {
    public static void checkNotNull(
            final @NotNull String name,
            final Object value
    ) {
        if (value == null) {
            throw new IllegalArgumentException("'" + name + "' is null!");
        }
    }

    public static void checkLength(
            final @NotNull String name,
            final int currentLength,
            final int requiredLength
    ) {
        if (currentLength != requiredLength) {
            throw new IllegalArgumentException("'" + name + "' has length " + currentLength + " when it must be " + requiredLength);
        }
    }
}
