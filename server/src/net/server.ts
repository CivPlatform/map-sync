import { serve, type Server, type ServerWebSocket } from "bun";

import { exists } from "../lang.ts";
import {
    type ClientPacket,
    decodePacket,
    encodePacketToBytes,
    type ServerPacket,
    UnexpectedPacket,
} from "./protocol.ts";
import { BufferReader } from "./buffers.ts";
import {
    ChunkTilePacket,
    ServerboundCatchupRequestPacket,
    ServerboundChunkTimestampsRequestPacket,
    ServerboundAuthResponsePacket,
    ServerboundHandshakePacket,
} from "./packets.ts";
import {
    handleConnected,
    handleAuthResponse,
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
    public readonly server: Server;
    public readonly clients = new Map<number, TcpClient>();

    public constructor(
        host: string,
        port: number,
        public readonly handlers: ProtocolHandler,
    ) {
        const self = this;

        this.server = serve<TcpClient, {}>({
            hostname: host,
            port: port,
            async fetch(req, server) {
                const url = URL.parse(req.url);
                if (url === null) {
                    return new Response(null, {
                        status: 400,
                    });
                }
                if (url.pathname !== "/") {
                    return new Response(null, {
                        status: 404,
                    });
                }
                if (!server.upgrade(req)) {
                    return new Response(null, {
                        status: 426,
                    });
                }
                // Bun automatically returns a 101 Switching Protocols
                return undefined;
            },
            websocket: {
                async open(socket) {
                    const client = new TcpClient(socket, self.handlers);
                    self.clients.set(client.id, (socket.data = client));
                    await handleConnected(client);
                    await self.handlers.handleClientConnected(client);
                    client.log("Connected");
                },
                async close(socket, err) {
                    const client: TcpClient = socket.data;
                    self.clients.delete(client.id);
                    if (exists(err)) {
                        client.warn(`Closed due to an error!`, err);
                    }
                    await self.handlers.handleClientDisconnected(client);
                    client.log("Disconnected");
                },
                async message(socket, message) {
                    const client: TcpClient = socket.data;
                    if (typeof message === "string") {
                        socket.close(1003, "String messages are not supported");
                        return;
                    }
                    try {
                        const reader = new BufferReader(message);
                        const packet = decodePacket(reader);
                        const remainder = reader.remainder;
                        if (remainder > 0) {
                            throw new Error(
                                `Packet did not consume all data! Remainder: [${remainder}]`,
                            );
                        }
                        await client.handlePacketReceived(packet);
                    } catch (err) {
                        client.warn(err);
                        client.kick("Error in packet handler");
                        return;
                    }
                },
            },
        });
        console.log("[WsServer] Listening on", host, port);
    }
}

let nextClientId = 1;

/** Prefixes packets with their length (UInt32BE);
 * handles Mojang authentication */
export class TcpClient {
    public readonly id = nextClientId++;
    /** contains mojang name once logged in */
    public name = "Client" + this.id;

    public gameAddress: string | null = null;
    public dimension: string | null = null;

    /** sent by client during handshake */
    public auth: any;

    public constructor(
        private socket: ServerWebSocket<TcpClient>,
        public handlers: ProtocolHandler,
    ) {}

    async handlePacketReceived(packet: ClientPacket) {
        this.debug("Received packet: " + packet.type.toString());
        switch (packet.type) {
            case ServerboundHandshakePacket.TYPE:
                await handleHandshake(
                    this,
                    packet as ServerboundHandshakePacket,
                );
                return;
            case ServerboundAuthResponsePacket.TYPE:
                await handleAuthResponse(
                    this,
                    packet as ServerboundAuthResponsePacket,
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
        this.socket.close();
    }

    public async send(packet: ServerPacket) {
        await this.sendRaw(packet.type, encodePacketToBytes(packet));
    }

    public async sendRaw(type: Symbol, raw: Buffer) {
        this.debug("Sending packet: " + type.toString());
        this.socket.sendBinary(raw);
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
