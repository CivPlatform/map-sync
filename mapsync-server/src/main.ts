import node_utils from "node:util";
import "./cli";
import { setServer } from "./cli";
import * as database from "./database";
import * as metadata from "./metadata";
import { TcpClient, TcpServer } from "./server";
import {
    ChunkTilePacket,
    ClientboundChunkTimestampsResponsePacket,
    ClientboundRegionTimestampsPacket,
    ServerboundCatchupRequestPacket,
    ServerboundChunkTimestampsRequestPacket,
    ServerboundPacket,
} from "./protocol";

let config: metadata.Config = null!;
let main: Main = null!;
Promise.resolve().then(async () => {
    await database.setup();

    config = metadata.getConfig();

    // These two are only used if whitelist is enabled... but best to load them
    // anyway lest there be a modification to them that is then saved.
    await metadata.loadWhitelist();
    await metadata.loadUuidCache();

    main = new Main();
    setServer(main.server);
});

type ProtocolClient = TcpClient; // TODO cleanup

export class Main {
    server: TcpServer = new TcpServer(this);

    //Cannot be async, as it's caled from a synchronous constructor
    handleClientConnected(client: ProtocolClient) {}

    async handleClientAuthenticated(client: ProtocolClient) {
        if (!client.uuid) throw new Error("Client not authenticated");

        metadata.cachePlayerUuid(client.mcName!, client.uuid!);
        await metadata.saveUuidCache();

        if (config.whitelist) {
            if (!metadata.whitelist.has(client.uuid)) {
                client.log(
                    `Rejected unwhitelisted user ${client.mcName} (${client.uuid})`,
                );
                client.kick(`Not whitelisted`);
                return;
            }
        }

        // TODO check version, mc server, user access

        const regions = await database.getRegionTimestamps(client.dimension!);
        await Promise.allSettled(
            regions.map((region) =>
                client.send(
                    new ClientboundRegionTimestampsPacket(
                        client.dimension!,
                        region.regionX,
                        region.regionZ,
                        region.timestamp,
                    ),
                ),
            ),
        );
    }

    handleClientDisconnected(client: ProtocolClient) {}

    async handleClientPacketReceived(
        client: ProtocolClient,
        pkt: ServerboundPacket,
    ) {
        switch (true) {
            case pkt instanceof ServerboundChunkTimestampsRequestPacket:
                return this.handleChunkTimestampsRequest(client, pkt);
            case pkt instanceof ServerboundCatchupRequestPacket:
                return this.handleCatchupRequest(client, pkt);
            case pkt instanceof ChunkTilePacket:
                return this.handleChunkTilePacket(client, pkt);
            default:
                throw new Error(
                    `Unknown packet [${node_utils.inspect(pkt)}] from client ${client.id}`,
                );
        }
    }

    async handleChunkTilePacket(client: ProtocolClient, pkt: ChunkTilePacket) {
        if (!client.uuid)
            throw new Error(`${client.name} is not authenticated`);

        // TODO ignore if same chunk hash exists in db

        await database
            .storeChunkData(
                pkt.dimension,
                pkt.chunkX,
                pkt.chunkZ,
                client.uuid,
                pkt.timestamp,
                pkt.dataVersion,
                pkt.dataHash,
                pkt.data,
            )
            .catch(console.error);

        // TODO small timeout, then skip if other client already has it
        for (const otherClient of Object.values(this.server.clients)) {
            if (client === otherClient) continue;
            otherClient.send(pkt);
        }
    }

    async handleCatchupRequest(
        client: ProtocolClient,
        pkt: ServerboundCatchupRequestPacket,
    ) {
        if (!client.uuid)
            throw new Error(`${client.name} is not authenticated`);

        for (const req of pkt.chunks) {
            let chunk = await database.getChunkData(
                pkt.dimension,
                req.chunkX,
                req.chunkZ,
            );
            if (!chunk) {
                console.error(`${client.name} requested unavailable chunk`, {
                    dimension: pkt.dimension,
                    ...req,
                });
                continue;
            }

            if (chunk.timestamp > req.timestamp) continue; // someone sent a new chunk, which presumably got relayed to the client
            if (chunk.timestamp < req.timestamp) continue; // the client already has a chunk newer than this

            client.send(
                new ChunkTilePacket(
                    pkt.dimension,
                    req.chunkX,
                    req.chunkZ,
                    req.timestamp,
                    chunk.version,
                    chunk.hash,
                    chunk.data,
                ),
            );
        }
    }

    async handleChunkTimestampsRequest(
        client: ProtocolClient,
        pkt: ServerboundChunkTimestampsRequestPacket,
    ) {
        if (!client.uuid)
            throw new Error(`${client.name} is not authenticated`);

        const chunks = await database.getChunkTimestamps(
            pkt.dimension,
            pkt.regionX,
            pkt.regionZ,
        );
        if (chunks.length) {
            client.send(
                new ClientboundChunkTimestampsResponsePacket(
                    pkt.dimension,
                    pkt.regionX,
                    pkt.regionZ,
                    chunks,
                ),
            );
        }
    }
}
