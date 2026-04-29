import { WebSocketServer, WebSocket } from "ws";
import { ProtocolHandler } from "./main.ts";
import {
    decodePacket,
    encodePacket,
    type ClientboundPacket,
} from "./packets.ts";
import { BufferReader, BufferWriter } from "./buffers.ts";
import { Welcomed, AuthState } from "./auth.ts";
import { type Config } from "./metadata.ts";

export class WSServer {
    public readonly wss: WebSocketServer;
    public readonly clients = new Map<number, WSClient>();

    public constructor(
        public readonly config: Config,
        public readonly handler: ProtocolHandler,
    ) {
        this.wss = new WebSocketServer(
            {
                port: config.port,
                path: "/",
                maxPayload: (1 << 16) - 1, // sizeof(u16)
            },
            () => {
                console.log("[WSServer] Listening on", config.port);
            },
        );

        this.wss.on("connection", (ws, req) => {
            const client = new WSClient(ws, this);
            this.clients.set(client.id, client);
            ws.on("close", () => this.clients.delete(client.id));
        });

        this.wss.on("error", (err: Error) => {
            console.error("[WSServer] Error:", err);
            this.wss.close();
        });
    }
}

let nextClientId = 1;

export class WSClient {
    public readonly id = nextClientId++;

    public get name() {
        let name = "Client" + this.id;
        const suffix = this.auth?.logName ?? null;
        if (suffix !== null) {
            name += ":" + suffix;
        }
        return name;
    }

    public auth: AuthState | null = null;
    public gameAddress: string | null = null;
    public dimension: string | null = null;

    public constructor(
        private readonly ws: WebSocket,
        server: WSServer,
    ) {
        this.log("Connected...");
        server.handler.handleClientConnected(this);

        ws.on("message", async (data, isBinary) => {
            if (!isBinary) {
                ws.close(1003);
                return;
            }
            switch (true) {
                case data instanceof ArrayBuffer:
                    data = Buffer.from(data);
                    break;
                case Array.isArray(data):
                    data = Buffer.concat(data);
                    break;
                default:
                    data = data as Buffer;
                    break;
            }
            try {
                const packet = decodePacket(new BufferReader(data));
                this.debug("Received", packet);
                await server.handler.handleClientPacketReceived(this, packet);
            } catch (err) {
                this.warn(err);
                return this.kick("Error in packet handler");
            }
        });

        ws.on("close", (code, reason) => {
            this.log("Closed.", { code, reason: reason.toString() });
            server.handler.handleClientDisconnected(this);
        });

        ws.on("error", (err) => {
            this.warn("Error:", err);
            this.kick("WebSocket error");
        });
    }

    public requireWelcomed(): Welcomed {
        if (this.auth instanceof Welcomed) {
            return this.auth;
        }
        throw new Error("Client is not authenticated!");
    }

    public isInDimension(dimension: string): boolean {
        return this.dimension === dimension;
    }

    public kick(internalReason: string) {
        this.log("Kicking:", internalReason);
        this.ws.close(1000);
    }

    public send(pkt: ClientboundPacket) {
        if (this.ws.readyState !== WebSocket.OPEN) {
            return this.debug("WebSocket not open, dropping", pkt);
        }
        this.debug("Sending", pkt);
        let buf: Buffer;
        {
            const packetWriter = new BufferWriter();
            encodePacket(pkt, packetWriter);
            buf = packetWriter.getBuffer();
        }
        this.ws.send(buf);
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
