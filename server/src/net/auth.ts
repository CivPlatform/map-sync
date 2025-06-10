import node_crypto from "node:crypto";

import { z } from "zod";
import { fromZodError } from "zod-validation-error";

import { type TcpClient } from "./server.ts";
import {
    ClientboundEncryptionRequestPacket,
    ServerboundEncryptionResponsePacket,
    type ServerboundHandshakePacket,
} from "./packets.ts";
import { UnexpectedPacket } from "./protocol.ts";
import { SUPPORTED_VERSIONS } from "../constants.ts";

const KEY_PAIR = node_crypto.generateKeyPairSync("rsa", {
    modulusLength: 1024,
});
const PUBLIC_KEY = KEY_PAIR.publicKey.export({
    type: "spki",
    format: "der",
});

// ============================================================
// Handshake
// ============================================================

class AwaitingHandshake {}

export async function handleConnected(client: TcpClient) {
    client.auth = new AwaitingHandshake();
}

export async function handleHandshake(
    client: TcpClient,
    packet: ServerboundHandshakePacket,
) {
    if (!(client.auth instanceof AwaitingHandshake)) {
        throw new UnexpectedPacket(packet.type.toString());
    }

    if (!SUPPORTED_VERSIONS.has(packet.modVersion)) {
        client.kick(
            `Connected with unsupported version [${packet.modVersion}]`,
        );
        return;
    }

    client.claimedMojangUsername = packet.mojangName;
    client.gameAddress = packet.gameAddress;
    client.dimension = packet.dimension;

    const verifyToken = node_crypto.randomBytes(4);

    client.auth = new AwaitingEncryptionResponse(verifyToken);
    await client.send(
        new ClientboundEncryptionRequestPacket(PUBLIC_KEY, verifyToken),
    );
}

// ============================================================
// Encryption Response
// ============================================================

export function decrypt(buf: Buffer): Buffer {
    return node_crypto.privateDecrypt(
        {
            key: KEY_PAIR.privateKey,
            padding: node_crypto.constants.RSA_PKCS1_PADDING,
        },
        buf,
    );
}

class AwaitingEncryptionResponse {
    public constructor(public readonly verifyToken: Buffer) {}
}

export async function handleEncryptionResponse(
    client: TcpClient,
    packet: ServerboundEncryptionResponsePacket,
) {
    if (!(client.auth instanceof AwaitingEncryptionResponse)) {
        throw new UnexpectedPacket(packet.type.toString());
    }

    const decryptedVerifyToken = decrypt(packet.verifyToken);
    if (!client.auth.verifyToken.equals(decryptedVerifyToken)) {
        client.kick("verifyToken does not match!");
        client.debug(
            `Expected [${client.auth.verifyToken.toHex()}], received [${decryptedVerifyToken.toHex()}]`,
        );
        return;
    }

    const decryptedSharedSecret = decrypt(packet.sharedSecret);
    client.ciphers = {
        encipher: node_crypto.createCipheriv(
            "aes-128-cfb8",
            decryptedSharedSecret,
            decryptedSharedSecret,
        ),
        decipher: node_crypto.createDecipheriv(
            "aes-128-cfb8",
            decryptedSharedSecret,
            decryptedSharedSecret,
        ),
    };
    client.debug("Connection is now encrypted!");

    if (Bun.env["MAPSYNC_DISABLE_AUTH"] === "true") {
        client.auth = new OfflineAuth(client.claimedMojangUsername!);
        client.name += "?:" + client.claimedMojangUsername!;
    } else {
        const auth = await fetchHasJoined(
            client,
            node_crypto
                .createHash("sha1")
                .update(decryptedSharedSecret)
                .update(PUBLIC_KEY)
                .digest()
                .toString("hex"),
        );
        if (auth === null) {
            client.kick("Not authenticated!");
            return;
        }

        client.auth = new OnlineAuth(auth.name, auth.uuid);
        client.name += ":" + auth.name;
    }

    await client.handlers.handleClientAuthenticated(client);
}

// ============================================================
// Authentication
// ============================================================

export class OfflineAuth {
    public constructor(public readonly name: string) {}
}

export class OnlineAuth {
    public constructor(
        public readonly name: string,
        public readonly uuid: string,
    ) {}
}

export function isAuthed(client: TcpClient) {
    return (
        client.auth instanceof OnlineAuth || client.auth instanceof OfflineAuth
    );
}

export function requireAuth(client: TcpClient) {
    if (!isAuthed(client)) {
        throw new Error("User not authenticated!");
    }
}

const MOJANG_AUTH_RESPONSE_SCHEMA = z.object({
    id: z.string().uuid(),
    name: z.string(),
});

async function fetchHasJoined(
    client: TcpClient,
    shaHex: string,
): Promise<{
    name: string;
    uuid: string;
} | null> {
    let url = `https://sessionserver.mojang.com/session/minecraft/hasJoined?username=${client.claimedMojangUsername!}&serverId=${shaHex}`;

    let response: Response;
    try {
        response = await fetch(url);
    } catch (error) {
        client.warn("Could not complete auth request!", error);
        return null;
    }
    if (response.status === 204) {
        return null;
    }

    let raw: unknown;
    try {
        raw = await response.json();
    } catch (error) {
        client.warn("Could not parse auth response as json!", error);
        return null;
    }

    let auth: z.infer<typeof MOJANG_AUTH_RESPONSE_SCHEMA>;
    try {
        auth = MOJANG_AUTH_RESPONSE_SCHEMA.parse(raw);
    } catch (error) {
        client.warn(
            "Could not validate auth response!",
            fromZodError(error as z.ZodError),
        );
        return null;
    }

    return {
        name: auth.name,
        uuid: auth.id.replace(
            /^(........)-?(....)-?(....)-?(....)-?(............)$/,
            "$1-$2-$3-$4-$5",
        ),
    };
}
