package gjum.minecraft.mapsync.common.utils;

import io.netty.buffer.ByteBuf;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public final class Hasher {
	private final MessageDigest messageDigest;

	private Hasher(
			final @NotNull MessageDigest messageDigest
	) {
		this.messageDigest = Objects.requireNonNull(messageDigest);
	}

	/**
	 * Updates the digest with a single byte.
	 */
	public @NotNull Hasher update(
			final byte input
	) {
		this.messageDigest.update((byte) input);
		return this;
	}

	/**
	 * Updates the digest with an entire byte array.
	 */
	public @NotNull Hasher update(
			final byte @NotNull [] input
	) {
		this.messageDigest.update((byte[]) input);
		return this;
	}

	/**
	 * Updates the digest with a byte-array slice, defined by the given offset and length.
	 */
	public @NotNull Hasher update(
			final byte @NotNull [] input,
			final int offset,
			final int length
	) {
		this.messageDigest.update((byte[]) input, offset, length);
		return this;
	}

	/**
	 * Updates the digest with a ByteBuffer slice, using {@link ByteBuffer#position()} as the offset and
	 * {@link ByteBuffer#remaining()} as the length. If you have been writing to this ByteBuffer, you may wish to
	 * {@link ByteBuffer#flip()} it first before passing it into this method.
	 */
	public @NotNull Hasher update(
			final @NotNull ByteBuffer input
	) {
		this.messageDigest.update((ByteBuffer) input);
		return this;
	}

	/**
	 * Updates the digest with a ByteBuffer slice, defined by the given offset and length.
	 */
	public @NotNull Hasher update(
			final @NotNull ByteBuffer input,
			final int offset,
			final int length
	) {
		return update((ByteBuffer) input.slice(offset, length));
	}

	/**
	 * Updates the digest with a ByteBuf slice, using {@link ByteBuf#readerIndex()} as the offset and
	 * {@link ByteBuf#readableBytes()} as the length.
	 */
	public @NotNull Hasher update(
			final @NotNull ByteBuf input
	) {
		update((ByteBuffer) input.nioBuffer());
		return this;
	}

	/**
	 * Updates the digest with a ByteBuf slice, defined by the given offset and length.
	 */
	public @NotNull Hasher update(
			final @NotNull ByteBuf input,
			final int offset,
			final int length
	) {
		update((ByteBuffer) input.nioBuffer(offset, length));
		return this;
	}

	public byte @NotNull [] generateHash() {
		return this.messageDigest.digest();
	}

	/**
	 * Since every implementation of Java is required to support SHA-1
	 * (<a href="https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/MessageDigest.html">source</a>)
	 * it's a safe bet that the algorithm exists.
	 */
	public static @NotNull Hasher sha1() {
		final MessageDigest messageDigest;
		try {
			messageDigest = MessageDigest.getInstance("SHA-1");
		}
		catch (final NoSuchAlgorithmException thrown) {
			throw new IllegalStateException("This should never happen!", thrown);
		}
		return new Hasher(messageDigest);
	}
}
