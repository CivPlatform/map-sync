import net from "node:net";
import { TcpClient } from "./client";

const { PORT = "12312", HOST = "127.0.0.1" } = process.env;

export class TcpServer {
    public readonly server: net.Server;
    public readonly clients = new Map<number, TcpClient>();

    public constructor() {
        this.server = net.createServer({}, (socket) => {
            const client = new TcpClient(socket, this);
            client.log("Connected from", socket.remoteAddress);
            this.clients.set(client.id, client);
            socket.on("end", () => this.clients.delete(client.id));
            socket.on("close", () => this.clients.delete(client.id));
        });
        this.server.on("error", (err: Error) => {
            console.error("[TcpServer] Error:", err);
            this.server.close();
        });
        this.server.listen({ port: PORT, hostname: HOST }, () => {
            console.log("[TcpServer] Listening on", HOST, PORT);
        });
    }
}
