import "./cli";
import { connectDB } from "./db";
import { PlayerChunk, PlayerChunkDB } from "./MapChunk";
import { uuid_cache, uuid_cache_save, getConfig, whitelist } from "./metadata";
import { ClientPacket } from "./protocol";
import { TcpClient } from "./server/client";
import { TcpServer } from "./server/server";
import {
    RegionTimestampsPacket,
    RegionCatchupRequestPacket,
    RegionCatchupResponsePacket,
    ChunkCatchupRequestPacket,
    ChunkDataPacket
} from "./protocol/packets";
import { RegionTimestamp } from "./protocol/structs";

export type ProtocolClient = TcpClient; // TODO cleanup
export type ProtocolHandler = Main; // TODO cleanup

// Have to do this because node doesn't have top-level await for CommonJS
Promise.resolve().then(async () => {
    await connectDB();
    const protocolHandler: ProtocolHandler = new Main();
    const server = new TcpServer(protocolHandler);
    protocolHandler.server = server; // TODO: Remove this with handler refactor
});

export class Main {
    server: TcpServer = null!;

    async handleChunkTilePacket(client: ProtocolClient, pkt: ChunkDataPacket) {
        if (!client.uuid)
            throw new Error(`${client.name} is not authenticated`);

        // TODO ignore if same chunk hash exists in db

        const playerChunk: PlayerChunk = {
            world: pkt.world,
            chunk_x: pkt.x,
            chunk_z: pkt.z,
            uuid: client.uuid,
            ts: pkt.timestamp,
            data: pkt.data
        };
        PlayerChunkDB.store(playerChunk).catch(console.error);

        // TODO small timeout, then skip if other client already has it
        for (const otherClient of this.server.clients.values()) {
            if (client === otherClient) continue;
            otherClient.send(pkt);
        }

        // TODO queue tile render for web map
    }

}
