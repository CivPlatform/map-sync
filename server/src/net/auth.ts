import node_crypto from "node:crypto";

import { z } from "zod/v4";

import { type TcpClient } from "./server.ts";
import {
    ClientboundAuthRequestPacket,
    ClientboundWelcomePacket,
    ServerboundAuthResponsePacket,
    type ServerboundHandshakePacket,
} from "./packets.ts";
import { UnexpectedPacket } from "./protocol.ts";
import { SUPPORTED_VERSIONS, UUID_REGEX } from "../constants.ts";
import { INT64_SIZE } from "../lang.ts";

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

    client.gameAddress = packet.gameAddress;
    client.dimension = packet.dimension;

    if (Bun.env["MAPSYNC_DISABLE_AUTH"] === "true") {
        client.auth = new OfflineAuth(packet.mojangName);
        client.name += "?:" + packet.mojangName;
        await client.send(new ClientboundWelcomePacket());
        return;
    }

    const serverSecret = node_crypto.randomBytes(INT64_SIZE);
    client.auth = new AwaitingAuthResponse(serverSecret, packet.mojangName);
    await client.send(new ClientboundAuthRequestPacket(serverSecret));
}

// ============================================================
// Encryption Response
// ============================================================

class AwaitingAuthResponse {
    public constructor(
        public readonly serverSecret: Buffer,
        public readonly claimedMojangUsername: string,
    ) {}
}

export async function handleAuthResponse(
    client: TcpClient,
    packet: ServerboundAuthResponsePacket,
) {
    if (!(client.auth instanceof AwaitingAuthResponse)) {
        throw new UnexpectedPacket(packet.type.toString());
    }

    const auth = await fetchHasJoined(
        client,
        client.auth.claimedMojangUsername,
        node_crypto
            .createHash("sha1")
            .update(packet.clientSecret)
            .update(client.auth.serverSecret)
            .digest()
            .toString("hex"),
    );
    if (auth === null) {
        client.kick("Not authenticated!");
        return;
    }

    client.auth = new OnlineAuth(auth.name, auth.uuid);
    client.name += ":" + auth.name;
    await client.send(new ClientboundWelcomePacket());

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
    id: z.string().regex(UUID_REGEX),
    name: z.string(),
});

async function fetchHasJoined(
    client: TcpClient,
    username: string,
    shaHex: string,
): Promise<{
    name: string;
    uuid: string;
} | null> {
    let url = `https://sessionserver.mojang.com/session/minecraft/hasJoined?username=${username}&serverId=${shaHex}`;

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
        client.warn("Could not validate auth response!");
        client.warn(z.prettifyError(error as z.ZodError));
        return null;
    }

    return {
        name: auth.name,
        uuid: auth.id.replace(UUID_REGEX, "$1-$2-$3-$4-$5"),
    };
}
