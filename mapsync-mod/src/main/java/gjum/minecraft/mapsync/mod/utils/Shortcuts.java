package gjum.minecraft.mapsync.mod.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.jetbrains.annotations.NotNull;

public final class Shortcuts {
	public static @NotNull MessageDigest shaHash() {
		try {
			return MessageDigest.getInstance("SHA-1");
		}
		catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException("unreachable", e);
		}
	}
}
