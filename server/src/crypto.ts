import node_crypto from "node:crypto";
export { randomBytes, createHash } from "node:crypto";

const KEY_PAIR = node_crypto.generateKeyPairSync("rsa", {
    modulusLength: 1024,
});
export const PUBLIC_KEY = KEY_PAIR.publicKey.export({
    type: "spki",
    format: "der",
});

export function decrypt(buf: Buffer): Buffer {
    return node_crypto.privateDecrypt(
        {
            key: KEY_PAIR.privateKey,
            padding: node_crypto.constants.RSA_PKCS1_PADDING,
        },
        buf,
    );
}

export type Ciphers = {
    encipher: node_crypto.Cipheriv;
    decipher: node_crypto.Decipheriv;
};
export function createCiphers(secret: Buffer): Ciphers {
    return {
        encipher: node_crypto.createCipheriv("aes-128-cfb8", secret, secret),
        decipher: node_crypto.createDecipheriv("aes-128-cfb8", secret, secret),
    };
}
