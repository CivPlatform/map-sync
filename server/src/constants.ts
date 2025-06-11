export const SUPPORTED_VERSIONS = new Set([
    "2.0.1-1.18.2+fabric",
    "2.0.1-1.18.2+forge",
]);

// SHA1 produces 160-bit (20-byte) hashes
// https://en.wikipedia.org/wiki/SHA-1
export const SHA1_HASH_LENGTH = 20;

export const UUID_REGEX =
    /^(........)-?(....)-?(....)-?(....)-?(............)$/;

// Sets the maximum frame length as the maximum 16-bit unsigned int value
// https://datatracker.ietf.org/doc/html/rfc6455#section-5.2
export const MAX_WS_FRAME_LENGTH = (1 << 16) - 1;
