import node_crypto from "crypto";
import { stringify as parseUuidBytes } from "uuid";
export { v5 as uuidv5, validate as isValidUuid } from "uuid";

// https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/UUID.html#nameUUIDFromBytes(byte[])
export function nameUuidFromBytes(bytes: Buffer): string {
    const hash = node_crypto.createHash("md5").update(bytes).digest();
    hash[6] &= 0x0f; // Clears the version
    hash[6] |= 0x30; // Sets version to 3
    hash[8] &= 0x3f; // Clears the variant
    hash[8] |= 0x80; // Sets variant to IETF
    return parseUuidBytes(hash);
}
