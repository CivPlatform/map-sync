import { listen, type Socket, type TCPSocketListener } from "bun";

import node_crypto from "node:crypto";

import { exists, INT32_SIZE } from "../lang.ts";
import {
    type ClientPacket,
    decodePacket,
    encodePacket,
    type ServerPacket,
    UnexpectedPacket,
} from "./protocol.ts";
import { BufferReader, BufferWriter } from "./buffers.ts";
import {
    ChunkTilePacket,
    ServerboundCatchupRequestPacket,
    ServerboundChunkTimestampsRequestPacket,
    ServerboundEncryptionResponsePacket,
    ServerboundHandshakePacket,
} from "./packets.ts";
import {
    handleConnected,
    handleEncryptionResponse,
    handleHandshake,
} from "./auth.ts";

export interface ProtocolHandler {
    handleClientConnected(client: TcpClient): Promise<void>;

    handleClientDisconnected(client: TcpClient): Promise<void>;

    handleClientAuthenticated(client: TcpClient): Promise<void>;

    handleClientPacketReceived(
        client: TcpClient,
        packet: ClientPacket,
    ): Promise<void>;
}

export class TcpServer {
    public readonly server: TCPSocketListener<TcpClient>;
    public readonly clients: Record<number, TcpClient> = {};

    public constructor(
        host: string,
        port: number,
        public readonly handlers: ProtocolHandler,
    ) {
        const self = this;
        this.server = listen<TcpClient>({
            hostname: host,
            port: port,
            socket: {
                binaryType: "buffer",
                async open(socket) {
                    const client = new TcpClient(socket, self.handlers);
                    self.clients[client.id] = socket.data = client;
                    await handleConnected(client);
                    await self.handlers.handleClientConnected(client);
                },
                async close(socket, err) {
                    const client: TcpClient = socket.data;
                    delete self.clients[client.id];
                    if (exists(err)) {
                        client.warn(`Closed due to an error!`, err);
                    }
                    await self.handlers.handleClientDisconnected(client);
                },
                async data(socket, data) {
                    const client: TcpClient = socket.data;
                    await client.handleReceivedData(data);
                },
            },
        });
        console.log("[TcpServer] Listening on", host, port);
    }
}

let nextClientId = 1;
const MAX_FRAME_SIZE = 2 ** 15;

/** Prefixes packets with their length (UInt32BE);
 * handles Mojang authentication */
export class TcpClient {
    public readonly id = nextClientId++;
    /** contains mojang name once logged in */
    public name = "Client" + this.id;

    public claimedMojangUsername: string | null = null;
    public gameAddress: string | null = null;
    public dimension: string | null = null;

    /** sent by client during handshake */
    public auth: any;
    public ciphers: {
        encipher: node_crypto.Cipheriv;
        decipher: node_crypto.Decipheriv;
    } | null = null;

    public constructor(
        private socket: Socket<TcpClient>,
        public handlers: ProtocolHandler,
    ) {
        this.log("Connected from", socket.remoteAddress);
    }

    static readonly #EMPTY_BUFFER = Buffer.allocUnsafe(0);
    #receivedBuffer: Buffer = TcpClient.#EMPTY_BUFFER;
    public async handleReceivedData(data: Buffer) {
        if (exists(this.ciphers)) {
            data = this.ciphers.decipher.update(data);
        }

        // creating a new buffer every time is fine in our case, because we expect most frames to be large
        this.#receivedBuffer = Buffer.concat([this.#receivedBuffer, data]);

        // we may receive multiple frames in one call
        while (true) {
            if (this.#receivedBuffer.byteLength <= INT32_SIZE) return; // wait for more data
            const frameSize = this.#receivedBuffer.readUInt32BE();

            // prevent Out of Memory
            if (frameSize > MAX_FRAME_SIZE) {
                return this.kick(
                    "Frame too large: " +
                        frameSize +
                        " have " +
                        this.#receivedBuffer.byteLength,
                );
            }

            if (this.#receivedBuffer.byteLength < INT32_SIZE + frameSize)
                return; // wait for more data

            const frameReader = new BufferReader(
                this.#receivedBuffer.subarray(INT32_SIZE),
            );
            const packetBuffer = frameReader.readBufLen(frameSize);
            this.#receivedBuffer = frameReader.readRemainder();

            try {
                const packet = decodePacket(new BufferReader(packetBuffer));
                await this.handlePacketReceived(packet);
            } catch (err) {
                this.warn(err);
                this.kick("Error in packet handler");
                return;
            }
        }
    }

    private async handlePacketReceived(packet: ClientPacket) {
        this.debug("Received packet: " + packet.type.toString());
        switch (packet.type) {
            case ServerboundHandshakePacket.TYPE:
                await handleHandshake(
                    this,
                    packet as ServerboundHandshakePacket,
                );
                return;
            case ServerboundEncryptionResponsePacket.TYPE:
                await handleEncryptionResponse(
                    this,
                    packet as ServerboundEncryptionResponsePacket,
                );
                return;
            case ServerboundChunkTimestampsRequestPacket.TYPE:
            case ServerboundCatchupRequestPacket.TYPE:
            case ChunkTilePacket.TYPE:
                await this.handlers.handleClientPacketReceived(this, packet);
                return;
            default:
                throw new UnexpectedPacket(packet.type.toString());
        }
    }

    public kick(internalReason: string) {
        this.log(`Kicking:`, internalReason);
        this.socket.end();
    }

    public async send(packet: ServerPacket) {
        const writer = new BufferWriter();
        writer.writeUnt32(0); // Placeholder for frame length, will write later
        encodePacket(packet, writer);

        let buffer = writer.getBuffer();
        buffer.writeUInt32BE(buffer.byteLength - INT32_SIZE, 0);

        if (exists(this.ciphers)) {
            buffer = this.ciphers.encipher.update(buffer);
        }

        this.socket.write(buffer);
    }

    public debug(...args: any[]) {
        if (process.env.NODE_ENV === "production") return;
        console.debug(`[${this.name}]`, ...args);
    }

    public log(...args: any[]) {
        console.log(`[${this.name}]`, ...args);
    }

    public warn(...args: any[]) {
        console.error(`[${this.name}]`, ...args);
    }
}
