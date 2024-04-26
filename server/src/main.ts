import "./cli";
import * as database from "./database";
import * as metadata from "./metadata";
import { ClientPacket } from "./protocol";
import { CatchupRequestPacket } from "./protocol/CatchupRequestPacket";
import { ChunkTilePacket } from "./protocol/ChunkTilePacket";
import { TcpClient, TcpServer } from "./server";
import { RegionCatchupPacket } from "./protocol/RegionCatchupPacket";

let config: metadata.Config = null!;
Promise.resolve().then(async () => {
    await database.setup();

    config = metadata.getConfig();

    // These two are only used if whitelist is enabled... but best to load them
    // anyway lest there be a modification to them that is then saved.
    await metadata.loadWhitelist();
    await metadata.loadUuidCache();

    new Main();
});

type ProtocolClient = TcpClient; // TODO cleanup

export class Main {
    server = new TcpServer(this);

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

        const timestamps = await database.getRegionTimestamps(client.world!);
        client.send({
            type: "RegionTimestamps",
            world: client.world!,
            regions: timestamps,
        });
    }

    handleClientDisconnected(client: ProtocolClient) {}

    handleClientPacketReceived(client: ProtocolClient, pkt: ClientPacket) {
        client.debug(client.mcName + " <- " + pkt.type);
        switch (pkt.type) {
            case "ChunkTile":
                return this.handleChunkTilePacket(client, pkt);
            case "CatchupRequest":
                return this.handleCatchupRequest(client, pkt);
            case "RegionCatchup":
                return this.handleRegionCatchupPacket(client, pkt);
            default:
                throw new Error(
                    `Unknown packet '${(pkt as any).type}' from client ${
                        client.id
                    }`,
                );
        }
    }

    async handleChunkTilePacket(client: ProtocolClient, pkt: ChunkTilePacket) {
        if (!client.uuid)
            throw new Error(`${client.name} is not authenticated`);

        // TODO ignore if same chunk hash exists in db

        await database
            .storeChunkData(
                pkt.world,
                pkt.chunk_x,
                pkt.chunk_z,
                client.uuid,
                pkt.ts,
                pkt.data.version,
                pkt.data.hash,
                pkt.data.data,
            )
            .catch(console.error);

        // TODO small timeout, then skip if other client already has it
        for (const otherClient of Object.values(this.server.clients)) {
            if (client === otherClient) continue;
            otherClient.send(pkt);
        }

        // TODO queue tile render for web map
    }

    async handleCatchupRequest(
        client: ProtocolClient,
        pkt: CatchupRequestPacket,
    ) {
        if (!client.uuid)
            throw new Error(`${client.name} is not authenticated`);

        for (const req of pkt.chunks) {
            let chunk = await database.getChunkData(
                pkt.world,
                req.chunkX,
                req.chunkZ,
            );
            if (!chunk) {
                console.error(`${client.name} requested unavailable chunk`, {
                    world: pkt.world,
                    ...req,
                });
                continue;
            }

            if (chunk.ts > req.timestamp) continue; // someone sent a new chunk, which presumably got relayed to the client
            if (chunk.ts < req.timestamp) continue; // the client already has a chunk newer than this

            client.send({
                type: "ChunkTile",
                world: pkt.world,
                chunk_x: req.chunkX,
                chunk_z: req.chunkX,
                ts: req.timestamp,
                data: {
                    hash: chunk.hash,
                    data: chunk.data,
                    version: chunk.version,
                },
            });
        }
    }

    async handleRegionCatchupPacket(
        client: ProtocolClient,
        pkt: RegionCatchupPacket,
    ) {
        if (!client.uuid)
            throw new Error(`${client.name} is not authenticated`);

        const chunks = await database.getChunkTimestamps(
            pkt.world,
            pkt.regions,
        );
        if (chunks.length)
            client.send({ type: "Catchup", world: pkt.world, chunks });
    }
}
