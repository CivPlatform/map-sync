import crypto from "crypto";
import net from "net";
import { WebSocketServer, WebSocket } from "ws";
import { Main } from "./main";
import {
    decodePacket,
    encodePacket,
    type ServerboundPacket,
    type ClientboundPacket,
    ServerboundHandshakePacket,
    ServerboundEncryptionResponsePacket,
    ClientboundEncryptionRequestPacket,
} from "./protocol";
import { BufferReader, BufferWriter } from "./protocol/buffers";
import { SUPPORTED_VERSIONS } from "./constants";
import * as metadata from "./metadata";
import { asUnt31 } from "./deps/ints";

const maxFrameSize = 2 ** 21; // 20KB
const { PORT = "12312", HOST = "127.0.0.1" } = process.env;

type ProtocolHandler = Main; // TODO cleanup

export class WSServer {
    wss: WebSocketServer;
    clients: Record<number, WSClient> = {};

    keyPair = crypto.generateKeyPairSync("rsa", { modulusLength: 1024 });
    // precomputed for networking
    publicKeyBuffer = this.keyPair.publicKey.export({
        type: "spki",
        format: "der",
    });

    constructor(readonly handler: ProtocolHandler) {
        this.wss = new WebSocketServer(
            {
                port: parseInt(PORT),
                host: HOST,
                path: "/ws",
                maxPayload: maxFrameSize,
            },
            () => {
                console.log("[WSServer] Listening on", HOST, PORT);
            },
        );

        this.wss.on("connection", (ws, req) => {
            const client = new WSClient(ws, this, handler);
            this.clients[client.id] = client;
            ws.on("close", () => delete this.clients[client.id]);
        });

        this.wss.on("error", (err: Error) => {
            console.error("[WSServer] Error:", err);
            this.wss.close();
        });
    }

    decrypt(buf: Buffer) {
        return crypto.privateDecrypt(
            {
                key: this.keyPair.privateKey,
                padding: crypto.constants.RSA_PKCS1_OAEP_PADDING,
                oaepHash: "sha256",
            },
            buf,
        );
    }
}

let nextClientId = 1;

export class WSClient {
    readonly id = nextClientId++;
    /** contains mojang name once logged in */
    name = "Client" + this.id;

    modVersion: string | undefined;
    gameAddress: string | undefined;
    uuid: string | undefined;
    mcName: string | undefined;
    dimension: string | undefined;

    /** prevent Out of Memory when client sends a large packet */
    maxFrameSize = maxFrameSize;

    /** sent by client during handshake */
    private claimedMojangName?: string;
    private verifyToken?: Buffer;
    /** we need to wait for the mojang auth response
     * before we can en/decrypt packets following the handshake */
    private cryptoPromise?: Promise<{
        cipher: crypto.Cipheriv;
        decipher: crypto.Decipheriv;
    }>;

    constructor(
        private readonly ws: WebSocket,
        private readonly server: WSServer,
        private readonly handler: ProtocolHandler,
    ) {
        // this.log("Connected from", ws.remoteAddress);
        // handler.handleClientConnected(this);

        /** Accumulates received data, containing none, one, or multiple frames; the last frame may be partial only. */
        let accBuf: Buffer = Buffer.alloc(0);

        ws.on("message", async (rawData) => {
            let data: Buffer;
            if (rawData instanceof ArrayBuffer) data = Buffer.from(rawData);
            else if (rawData instanceof Buffer) data = rawData;
            else if (Array.isArray(rawData)) data = Buffer.concat(rawData);
            else {
                this.warn("Unknown data type:", typeof rawData);
                return;
            }

            try {
                if (this.cryptoPromise) {
                    const { decipher } = await this.cryptoPromise;
                    data = decipher.update(data);
                }

                // creating a new buffer every time is fine in our case, because we expect most frames to be large
                accBuf = Buffer.concat([accBuf, data]);

                // we may receive multiple frames in one call
                while (true) {
                    if (accBuf.length <= 4) return; // wait for more data
                    const frameSize = accBuf.readUInt32BE();

                    // prevent Out of Memory
                    if (frameSize > this.maxFrameSize) {
                        this.kick(
                            `Frame's length [${frameSize}] is too large, max is [${this.maxFrameSize}]!`,
                        );
                        return;
                    }

                    if (accBuf.length < 4 + frameSize) return; // wait for more data

                    const frameReader = new BufferReader(accBuf);
                    frameReader.readUnt31(); // skip frame size
                    let pktBuf = frameReader.readBytesOfLength(frameSize);
                    accBuf = frameReader.readRemainder();

                    const reader = new BufferReader(pktBuf);

                    try {
                        const packet = decodePacket(reader);
                        await this.handlePacketReceived(packet);
                    } catch (err) {
                        this.warn(err);
                        return this.kick("Error in packet handler");
                    }
                }
            } catch (err) {
                this.warn(err);
                return this.kick("Error in data handler");
            }
        });

        ws.on("close", (code, reason) => {
            this.log("Closed.", { code, reason: reason.toString() });
        });

        ws.on("error", (err) => {
            this.warn("Error:", err);
            this.kick("WebSocket error");
        });
    }

    private async handlePacketReceived(pkt: ServerboundPacket) {
        this.debug(`Received ${pkt.name}:`, pkt);
        if (!this.uuid) {
            switch (true) {
                case pkt instanceof ServerboundHandshakePacket:
                    return await this.handleHandshakePacket(pkt);
                case pkt instanceof ServerboundEncryptionResponsePacket:
                    return await this.handleEncryptionResponsePacket(pkt);
                default:
                    throw new Error(
                        `Packet ${pkt.name} from unauth'd client ${this.id}`,
                    );
            }
        } else {
            return await this.handler.handleClientPacketReceived(this, pkt);
        }
    }

    kick(internalReason: string) {
        this.log(`Kicking:`, internalReason);
        this.ws.close(0, internalReason);
    }

    async send(pkt: ClientboundPacket) {
        if (!this.cryptoPromise) {
            this.debug("Not encrypted, dropping packet", pkt);
            return;
        }
        if (!this.uuid) {
            this.debug("Not authenticated, dropping packet", pkt);
            return;
        }
        await this.sendInternal(pkt, true);
    }

    /**
     * Sends a packet to the client through the socket, optionally encrypting it.
     *
     * @param pkt - The packet to send, encoded as a `ServerPacket`.
     * @param doCrypto - If `true`, the packet is encrypted before sending. Defaults to `false`.
     * @throws If encryption is requested but the handshake is not finished.
     *
     * @remarks
     * - Reserves space for the packet length, encodes the packet, and writes the actual length.
     * - If encryption is enabled, waits for the handshake to complete and encrypts the buffer.
     * - Drops the packet if the socket is not writable.
     */
    private async sendInternal(pkt: ClientboundPacket, doCrypto = false) {
        if (this.ws.readyState !== WebSocket.OPEN)
            return this.debug("WebSocket not open, dropping", pkt);
        if (doCrypto && !this.cryptoPromise)
            throw new Error(`Can't encrypt: handshake not finished`);
        this.debug(`Sending ${pkt.name}:`, pkt);
        let buf: Buffer;
        {
            const packetWriter = new BufferWriter();
            encodePacket(pkt, packetWriter);
            buf = packetWriter.getBuffer();
        }
        {
            const frameWriter = new BufferWriter(asUnt31(4 + buf.length));
            frameWriter.writeUnt31(buf.length);
            frameWriter.writeBytes(buf);
            buf = frameWriter.getBuffer();
        }
        if (doCrypto) {
            const { cipher } = await this.cryptoPromise!;
            buf = cipher!.update(buf);
        }
        this.ws.send(buf);
    }

    private async handleHandshakePacket(packet: ServerboundHandshakePacket) {
        if (this.cryptoPromise) throw new Error(`Already authenticated`);
        if (this.verifyToken) throw new Error(`Encryption already started`);

        if (!SUPPORTED_VERSIONS.has(packet.modVersion)) {
            this.kick(
                "Connected with unsupported version [" +
                    packet.modVersion +
                    "]",
            );
            return;
        }

        this.gameAddress = packet.gameAddress;
        this.claimedMojangName = packet.mojangName;
        this.dimension = packet.dimension;
        this.verifyToken = crypto.randomBytes(4);

        await this.sendInternal(
            new ClientboundEncryptionRequestPacket(
                this.server.publicKeyBuffer,
                this.verifyToken,
            ),
        );
    }

    private async handleEncryptionResponsePacket(
        pkt: ServerboundEncryptionResponsePacket,
    ) {
        if (this.cryptoPromise) throw new Error(`Already authenticated`);
        if (!this.claimedMojangName)
            throw new Error(`Encryption has not started: no mojangName`);
        if (!this.verifyToken)
            throw new Error(`Encryption has not started: no verifyToken`);

        const verifyToken = this.server.decrypt(pkt.verifyToken);
        if (!this.verifyToken.equals(verifyToken)) {
            throw new Error(
                `verifyToken mismatch: got ${verifyToken} expected ${this.verifyToken}`,
            );
        }

        const secret = this.server.decrypt(pkt.sharedSecret);

        const shaHex = crypto
            .createHash("sha1")
            .update(secret)
            .update(this.server.publicKeyBuffer)
            .digest()
            .toString("hex");

        this.cryptoPromise = fetchHasJoined({
            username: this.claimedMojangName,
            shaHex,
        }).then(async (mojangAuth) => {
            if (!mojangAuth?.uuid) {
                this.kick(`Mojang auth failed`);
                throw new Error(`Mojang auth failed`);
            }

            this.log("Authenticated as", mojangAuth);

            this.uuid = mojangAuth.uuid;
            this.mcName = mojangAuth.name;
            this.name += ":" + mojangAuth.name;

            return {
                cipher: crypto.createCipheriv("aes-128-cfb8", secret, secret),
                decipher: crypto.createDecipheriv(
                    "aes-128-cfb8",
                    secret,
                    secret,
                ),
            };
        });

        await this.cryptoPromise.then(async () => {
            await this.handler.handleClientAuthenticated(this);
        });
    }

    debug(...args: any[]) {
        if (process.env.NODE_ENV === "production") return;
        console.debug(`[${this.name}]`, ...args);
    }

    log(...args: any[]) {
        console.log(`[${this.name}]`, ...args);
    }

    warn(...args: any[]) {
        console.error(`[${this.name}]`, ...args);
    }
}

async function fetchHasJoined(args: {
    username: string;
    shaHex: string;
    clientIp?: string;
}) {
    const { username, shaHex, clientIp } = args;

    // if auth is disabled, return a "usable" item
    if (!metadata.getConfig().auth)
        return { name: username, uuid: `AUTH-DISABLED-${username}` };

    let url = `https://sessionserver.mojang.com/session/minecraft/hasJoined?username=${username}&serverId=${shaHex}`;
    if (clientIp) url += `&ip=${clientIp}`;
    const res = await fetch(url);
    try {
        if (res.status === 204) return null;
        let { id, name } = (await res.json()) as { id: string; name: string };
        const uuid = id.replace(
            /^(........)-?(....)-?(....)-?(....)-?(............)$/,
            "$1-$2-$3-$4-$5",
        );
        return { uuid, name };
    } catch (err) {
        console.error(res);
        throw err;
    }
}
