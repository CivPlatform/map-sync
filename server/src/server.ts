import { type TCPSocketListener, type Socket, listen } from "bun";
import * as crypto from "./crypto.ts";
import type { ClientPacket, ServerPacket } from "./protocol";
import { decodePacket, encodePacket } from "./protocol";
import { BufReader } from "./protocol/BufReader";
import { BufWriter } from "./protocol/BufWriter";
import { EncryptionResponsePacket } from "./protocol/EncryptionResponsePacket";
import { HandshakePacket } from "./protocol/HandshakePacket";
import { SUPPORTED_VERSIONS } from "./constants";

export interface ProtocolHandler {
    handleClientConnected(
        client: TcpClient
    ): Promise<void>

    handleClientDisconnected(
        client: TcpClient
    ): Promise<void>

    handleClientAuthenticated(
        client: TcpClient
    ): Promise<void>

    handleClientPacketReceived(
        client: TcpClient,
        packet: ClientPacket
    ): Promise<void>
}

export class TcpServer {
    server: TCPSocketListener<TcpClient>;
    clients: Record<number, TcpClient> = {};

    constructor(
        host: string,
        port: number,
        readonly handler: ProtocolHandler
    ) {
        const self = this;
        this.server = listen<TcpClient>({
            hostname: host,
            port: port,
            socket: {
                binaryType: "buffer",
                async open(socket) {
                    const client = new TcpClient(socket, self, handler);
                    self.clients[client.id] = socket.data = client;
                    await self.handler.handleClientConnected(client);
                },
                async close(socket, err) {
                    const client: TcpClient = socket.data;
                    delete self.clients[client.id];
                    if ((err ?? null) !== null) {
                        client.warn(`Closed due to an error!`, err);
                    }
                    await self.handler.handleClientDisconnected(client);
                },
                async data(socket, data) {
                    const client: TcpClient = socket.data;
                    await client.handleReceivedData(data);
                },
            }
        });
        console.log("[TcpServer] Listening on", host, port);
    }
}

let nextClientId = 1;

/** Prefixes packets with their length (UInt32BE);
 * handles Mojang authentication */
export class TcpClient {
    readonly id = nextClientId++;
    /** contains mojang name once logged in */
    name = "Client" + this.id;

    modVersion: string | undefined;
    gameAddress: string | undefined;
    uuid: string | undefined;
    mcName: string | undefined;
    world: string | undefined;

    /** prevent Out of Memory when client sends a large packet */
    maxFrameSize = 2 ** 15;

    /** sent by client during handshake */
    private claimedMojangName?: string;
    private verifyToken?: Buffer;
    /** we need to wait for the mojang auth response
     * before we can en/decrypt packets following the handshake */
    private ciphers: crypto.Ciphers | null = null;

    constructor(
        private socket: Socket<TcpClient>,
        private server: TcpServer,
        private handler: ProtocolHandler,
    ) {
        this.log("Connected from", socket.remoteAddress);
    }

    static readonly #EMPTY_BUFFER = Buffer.allocUnsafe(0);
    #receivedBuffer: Buffer = TcpClient.#EMPTY_BUFFER;
    public async handleReceivedData(
        data: Buffer
    ) {
        if (this.ciphers) {
            data = this.ciphers.decipher.update(data);
        }

        // creating a new buffer every time is fine in our case, because we expect most frames to be large
        this.#receivedBuffer = Buffer.concat([
            this.#receivedBuffer,
            data
        ]);

        // we may receive multiple frames in one call
        while (true) {
            if (this.#receivedBuffer.byteLength <= 4) return; // wait for more data
            const frameSize = this.#receivedBuffer.readUInt32BE();

            // prevent Out of Memory
            if (frameSize > this.maxFrameSize) {
                return this.kick(
                    "Frame too large: " +
                    frameSize +
                    " have " +
                    this.#receivedBuffer.byteLength,
                );
            }

            if (this.#receivedBuffer.byteLength < 4 + frameSize) return; // wait for more data

            const frameReader = new BufReader(this.#receivedBuffer.subarray(4));
            const packetBuffer = frameReader.readBufLen(frameSize);
            this.#receivedBuffer = frameReader.readRemainder();

            try {
                const packet = decodePacket(new BufReader(packetBuffer));
                await this.handlePacketReceived(packet);
            }
            catch (err) {
                this.warn(err);
                this.kick("Error in packet handler");
                return;
            }
        }
    }

    private async handlePacketReceived(pkt: ClientPacket) {
        if (!this.uuid) {
            // not authenticated yet
            switch (pkt.type) {
                case "Handshake":
                    return await this.handleHandshakePacket(pkt);
                case "EncryptionResponse":
                    return await this.handleEncryptionResponsePacket(pkt);
            }
            throw new Error(
                `Packet ${pkt.type} from unauth'd client ${this.id}`,
            );
        } else {
            return await this.handler.handleClientPacketReceived(this, pkt);
        }
    }

    kick(internalReason: string) {
        this.log(`Kicking:`, internalReason);
        this.socket.end();
    }

    async send(pkt: ServerPacket) {
        if (!this.ciphers) {
            this.debug("Not encrypted, dropping packet", pkt.type);
            return;
        }
        if (!this.uuid) {
            this.debug("Not authenticated, dropping packet", pkt.type);
            return;
        }
        this.debug(this.mcName + " -> " + pkt.type);
        await this.sendInternal(pkt, true);
    }

    private async sendInternal(pkt: ServerPacket, doCrypto = false) {
        if (this.socket.readyState <= 0)
            return this.debug("Socket closed, dropping", pkt.type);
        if (doCrypto && !this.ciphers)
            throw new Error(`Can't encrypt: handshake not finished`);

        const writer = new BufWriter(); // TODO size hint
        writer.writeUnt32(0); // set later, but reserve space in buffer
        encodePacket(pkt, writer);
        let buf = writer.getBuffer();
        buf.writeUInt32BE(buf.length - 4, 0); // write into space reserved above

        if (doCrypto) {
            buf = this.ciphers!.encipher.update(buf);
        }

        this.socket.write(buf);
    }

    private async handleHandshakePacket(packet: HandshakePacket) {
        if (this.ciphers) throw new Error(`Already authenticated`);
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
        this.world = packet.dimension;
        this.verifyToken = crypto.randomBytes(4);

        await this.sendInternal({
            type: "EncryptionRequest",
            publicKey: crypto.PUBLIC_KEY,
            verifyToken: this.verifyToken,
        });
    }

    private async handleEncryptionResponsePacket(
        pkt: EncryptionResponsePacket,
    ) {
        if (this.ciphers) throw new Error(`Already authenticated`);
        if (!this.claimedMojangName)
            throw new Error(`Encryption has not started: no mojangName`);
        if (!this.verifyToken)
            throw new Error(`Encryption has not started: no verifyToken`);

        const verifyToken = crypto.decrypt(pkt.verifyToken);
        if (!this.verifyToken.equals(verifyToken)) {
            throw new Error(
                `verifyToken mismatch: got ${verifyToken} expected ${this.verifyToken}`,
            );
        }

        const secret = crypto.decrypt(pkt.sharedSecret);

        const shaHex = crypto
            .createHash("sha1")
            .update(secret)
            .update(crypto.PUBLIC_KEY)
            .digest()
            .toString("hex");

        this.ciphers = await fetchHasJoined({
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

            return crypto.createCiphers(secret);
        });

        await this.handler.handleClientAuthenticated(this);
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
    if ("DISABLE_AUTH" in process.env)
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
