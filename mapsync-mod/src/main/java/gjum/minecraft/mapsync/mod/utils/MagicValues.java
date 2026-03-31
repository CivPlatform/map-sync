package gjum.minecraft.mapsync.mod.utils;

import gjum.minecraft.mapsync.mod.MapSyncMod;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.lang3.IntegerRange;

public final class MagicValues {
	public static final String VERSION; static {
		final InputStream in = MapSyncMod.class.getResourceAsStream("/mapsync.version.const");
		if (in == null) {
			throw new ExceptionInInitializerError(new NullPointerException("'mapsync.version.const' const is missing!"));
		}
		try (in) {
			VERSION = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
		}
		catch (final IOException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	public static final int REGION_AXIS = 32;
	public static final int REGION_GRID = REGION_AXIS * REGION_AXIS;

	// SHA1 produces 160-bit (20-byte) hashes
	// https://en.wikipedia.org/wiki/SHA-1
	public static final int SHA1_HASH_LENGTH = 20;

	public static final int UNT1_MASK = 0x01;
	public static final int UNT5_MASK = 0x1F;
	public static final int UNT8_MASK = 0xFF;
	public static final int UNT10_MASK = 0x03_FF;
	public static final int UNT16_MASK = 0xFF_FF;

	public static final IntegerRange UNT5_RANGE = IntegerRange.of(0, UNT5_MASK);
	public static final IntegerRange UNT8_RANGE = IntegerRange.of(0, UNT8_MASK);
	public static final IntegerRange UNT10_RANGE = IntegerRange.of(0, UNT10_MASK);
	public static final IntegerRange UNT16_RANGE = IntegerRange.of(0, UNT16_MASK);
	public static final IntegerRange INT16_RANGE = IntegerRange.of(Short.MIN_VALUE, Short.MAX_VALUE);

	// https://www.rfc-editor.org/rfc/rfc6455#section-7.4.1
	public static final int CLOSE_CODE_KICKED = 4001;
}
