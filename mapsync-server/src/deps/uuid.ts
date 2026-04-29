import node_crypto from "crypto";
import { stringify as parseUuidBytes } from "uuid";
import { v5 as _uuidv5 } from "uuid";
export { validate as isValidUuid } from "uuid";

export const uuidv5 = _uuidv5;

// https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/UUID.html#nameUUIDFromBytes(byte[])
export function nameUuidFromBytes(bytes: Buffer): string {
    const hash = node_crypto.createHash("md5").update(bytes).digest();
    hash[6] &= 0x0f; // Clears the version
    hash[6] |= 0x30; // Sets version to 3
    hash[8] &= 0x3f; // Clears the variant
    hash[8] |= 0x80; // Sets variant to IETF
    return parseUuidBytes(hash);
}

export const UUID_NAMESPACE = nameUuidFromBytes(
    Buffer.from("mapsync:server", "utf8"),
);

export function createOfflineUuid(name: string) {
    return uuidv5(`Offline:${name}`, UUID_NAMESPACE);
}
