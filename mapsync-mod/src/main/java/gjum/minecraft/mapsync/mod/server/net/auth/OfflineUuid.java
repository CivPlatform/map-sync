package gjum.minecraft.mapsync.mod.server.net.auth;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/// Deterministic UUID for unauthenticated ("offline") connections, matching
/// the format mapsync-server (TypeScript) used so a db.sqlite migrated from
/// an old standalone deployment keeps resolving the same UUIDs:
/// `v5(nameUUIDFromBytes("mapsync:server"), "Offline:" + name)`.
///
/// Java's stdlib only ships `UUID.nameUUIDFromBytes` (v3 / MD5). The v5
/// (SHA-1) case is implemented here. The namespace itself stays as MD5 to
/// match the original — a v3 UUID derived from the literal "mapsync:server".
public final class OfflineUuid {
	private static final @NotNull UUID NAMESPACE =
		UUID.nameUUIDFromBytes("mapsync:server".getBytes(StandardCharsets.UTF_8));

	private OfflineUuid() {
	}

	public static @NotNull UUID forName(
		final @NotNull String name
	) {
		return v5(NAMESPACE, "Offline:" + name);
	}

	private static @NotNull UUID v5(
		final @NotNull UUID namespace,
		final @NotNull String name
	) {
		try {
			final MessageDigest md = MessageDigest.getInstance("SHA-1");
			md.update(uuidToBytes(namespace));
			md.update(name.getBytes(StandardCharsets.UTF_8));
			final byte[] hash = md.digest();
			hash[6] &= 0x0F;
			hash[6] |= 0x50; // version 5
			hash[8] &= 0x3F;
			hash[8] |= (byte) 0x80; // RFC 4122 variant
			return bytesToUuid(hash);
		}
		catch (final Exception e) {
			// SHA-1 is a guaranteed JDK algorithm; if it's missing the JVM is broken.
			throw new IllegalStateException("SHA-1 unavailable", e);
		}
	}

	private static byte @NotNull [] uuidToBytes(
		final @NotNull UUID uuid
	) {
		final ByteBuffer buf = ByteBuffer.allocate(16);
		buf.putLong(uuid.getMostSignificantBits());
		buf.putLong(uuid.getLeastSignificantBits());
		return buf.array();
	}

	private static @NotNull UUID bytesToUuid(
		final byte @NotNull [] bytes
	) {
		final ByteBuffer buf = ByteBuffer.wrap(bytes, 0, 16);
		final long msb = buf.getLong();
		final long lsb = buf.getLong();
		return new UUID(msb, lsb);
	}
}
