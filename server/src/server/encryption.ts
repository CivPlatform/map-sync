import crypto from "node:crypto";

const KEY_PAIR = crypto.generateKeyPairSync("rsa", { modulusLength: 1024 });
export const PUBLIC_KEY_BUFFER = KEY_PAIR.publicKey.export({
    type: "spki",
    format: "der"
});

export async function decrypt(buffer: Buffer): Promise<Buffer> {
    return crypto.privateDecrypt(
        {
            key: KEY_PAIR.privateKey,
            padding: crypto.constants.RSA_PKCS1_PADDING
        },
        buffer
    );
}

export type Ciphers = {
    readonly encipher: crypto.Cipher;
    readonly decipher: crypto.Decipher;
};

export async function generateCiphers(secret: Buffer): Promise<Ciphers> {
    const algorithm = "aes-128-cfb8";
    return {
        encipher: crypto.createCipheriv(algorithm, secret, secret),
        decipher: crypto.createDecipheriv(algorithm, secret, secret)
    };
}

export async function generateShaHash(data: Buffer): Promise<Buffer> {
    return crypto
        .createHash("sha1")
        .update(data)
        .digest();
}

export async function generateShaHex(secret: Buffer): Promise<string> {
    return crypto
        .createHash("sha1")
        .update(secret)
        .update(PUBLIC_KEY_BUFFER)
        .digest()
        .toString("hex");
}
