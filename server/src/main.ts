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

    handleClientPacketReceived(client: ProtocolClient, pkt: ClientPacket) {
        client.debug(client.mcName + " <- " + pkt.type);
        switch (pkt.type) {
            case "ChunkTile":
                return this.handleChunkTilePacket(
                    client,
                    pkt as ChunkDataPacket
                );
            case "CatchupRequest":
                return this.handleCatchupRequest(
                    client,
                    pkt as ChunkCatchupRequestPacket
                );
            case "RegionCatchup":
                return this.handleRegionCatchupPacket(
                    client,
                    pkt as RegionCatchupRequestPacket
                );
            default:
                throw new Error(
                    `Unknown packet '${(pkt as any).type}' from client ${
                        client.id
                    }`
                );
        }
    }

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

    async handleCatchupRequest(
        client: ProtocolClient,
        pkt: ChunkCatchupRequestPacket
    ) {
        if (!client.uuid)
            throw new Error(`${client.name} is not authenticated`);

        for (const req of pkt.chunks) {
            const { world, chunk_x, chunk_z } = req;

            let chunk = await PlayerChunkDB.getChunkWithData({
                world,
                chunk_x,
                chunk_z
            });
            if (!chunk) {
                console.error(
                    `${client.name} requested unavailable chunk`,
                    req
                );
                continue;
            }

            if (chunk.ts > req.ts) continue; // someone sent a new chunk, which presumably got relayed to the client
            if (chunk.ts < req.ts) continue; // the client already has a chunk newer than this

            client.send(new ChunkDataPacket(
                world,
                chunk_x,
                chunk_z,
                chunk.ts,
                chunk.data
            ));
        }
    }

    async handleRegionCatchupPacket(
        client: ProtocolClient,
        pkt: RegionCatchupRequestPacket
    ) {
        if (!client.uuid)
            throw new Error(`${client.name} is not authenticated`);

        const chunks = await PlayerChunkDB.getCatchupData(
            pkt.world,
            pkt.regions
                .map((region) => [region.x, region.z])
                .flat()
        );
        if (chunks.length > 0) {
            client.send(new RegionCatchupResponsePacket(
                pkt.world,
                chunks
            ));
        }
    }
}
