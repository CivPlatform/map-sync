package gjum.minecraft.mapsync.mod.utils;

public final class MagicValues {
	// SHA1 produces 160-bit (20-byte) hashes
	// https://en.wikipedia.org/wiki/SHA-1
	public static final int SHA1_HASH_LENGTH = 20;

	public static final int UNT5_MASK = 0x1F;
	public static final int UNT8_MASK = 0xFF;
	public static final int UNT10_MASK = 0x03_FF;
	public static final int UNT16_MASK = 0xFF_FF;
}
