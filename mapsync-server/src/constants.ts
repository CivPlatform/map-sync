import { asInt64 } from "./deps/ints.ts";

export const SUPPORTED_VERSIONS = new Set(["2.2.0-SNAPSHOT-1.21.11"]);

// SHA1 produces 160-bit (20-byte) hashes
// https://en.wikipedia.org/wiki/SHA-1
export const SHA1_HASH_LENGTH = 20;

// hold over until we implement proper seed handling
export const UNKNOWN_SEED = asInt64(-1);
