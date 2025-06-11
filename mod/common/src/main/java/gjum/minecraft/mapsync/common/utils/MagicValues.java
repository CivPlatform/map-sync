package gjum.minecraft.mapsync.common.utils;

public final class MagicValues {
    // SHA1 produces 160-bit (20-byte) hashes
    // https://en.wikipedia.org/wiki/SHA-1
    public static final int SHA1_HASH_LENGTH = 20;

    // Sets the maximum frame length as the maximum 16-bit unsigned int value
    // https://datatracker.ietf.org/doc/html/rfc6455#section-5.2
    public static final int MAX_WS_FRAME_SIZE = (1 << 16) - 1;
}
