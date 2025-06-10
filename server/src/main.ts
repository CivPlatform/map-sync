import "./cli";
import * as database from "./database";
import * as metadata from "./metadata";
import { type ClientPacket } from "./protocol";
import { CatchupRequestPacket } from "./protocol/CatchupRequestPacket";
import { ChunkTilePacket } from "./protocol/ChunkTilePacket";
import { type ProtocolHandler, TcpClient, TcpServer } from "./server";
import { RegionCatchupPacket } from "./protocol/RegionCatchupPacket";

let config: metadata.Config = null!;
Promise.resolve().then(async () => {
    await database.setup();

    config = metadata.getConfig();

    // These two are only used if whitelist is enabled... but best to load them
    // anyway lest there be a modification to them that is then saved.
    await metadata.loadWhitelist();
    await metadata.loadUuidCache();

    const server = new TcpServer(config.host, config.port, new class implements ProtocolHandler {
        public async handleClientConnected(
            client: TcpClient
        ) {

        }

        public async handleClientDisconnected(
            client: TcpClient
        ) {

        }

        public async handleClientAuthenticated(
            client: TcpClient
        ) {
            if (!client.uuid) {
                throw new Error("Client not authenticated");
            }

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
            await client.send({
                type: "RegionTimestamps",
                dimension: client.world!,
                regions: timestamps,
            });
        }

        public async handleClientPacketReceived(
            client: TcpClient,
            packet: ClientPacket
        ) {
            client.debug(client.mcName + " <- " + packet.type);
            switch (packet.type) {
                case "ChunkTile":
                    return this.handleChunkTilePacket(client, packet);
                case "CatchupRequest":
                    return this.handleCatchupRequest(client, packet);
                case "RegionCatchup":
                    return this.handleRegionCatchupPacket(client, packet);
                default:
                    throw new Error(
                        `Unknown packet '${(packet as any).type}' from client ${
                            client.id
                        }`,
                    );
            }
        }

        private async handleChunkTilePacket(
            client: TcpClient,
            packet: ChunkTilePacket
        ) {
            if (!client.uuid) {
                throw new Error(`${client.name} is not authenticated`);
            }

            // TODO ignore if same chunk hash exists in db

            await database
                .storeChunkData(
                    packet.dimension,
                    packet.chunk_x,
                    packet.chunk_z,
                    client.uuid,
                    packet.ts,
                    packet.data.version,
                    packet.data.hash,
                    packet.data.data,
                )
                .catch(console.error);

            // TODO small timeout, then skip if other client already has it
            await Promise.allSettled(
                Object.values(server.clients)
                    .filter((other) => other !== client && (other.uuid ?? null) !== null)
                    .map((other) => other.send(packet))
            );

            // TODO queue tile render for web map
        }

        private async handleCatchupRequest(
            client: TcpClient,
            packet: CatchupRequestPacket,
        ) {
            if (!client.uuid) {
                throw new Error(`${client.name} is not authenticated`);
            }

            for (const req of packet.chunks) {
                let chunk = await database.getChunkData(
                    packet.dimension,
                    req.chunkX,
                    req.chunkZ,
                );
                if (!chunk) {
                    console.error(`${client.name} requested unavailable chunk`, {
                        world: packet.dimension,
                        ...req,
                    });
                    continue;
                }

                if (chunk.ts > req.timestamp) continue; // someone sent a new chunk, which presumably got relayed to the client
                if (chunk.ts < req.timestamp) continue; // the client already has a chunk newer than this

                await client.send({
                    type: "ChunkTile",
                    dimension: packet.dimension,
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

        private async handleRegionCatchupPacket(
            client: TcpClient,
            packet: RegionCatchupPacket,
        ) {
            if (!client.uuid) {
                throw new Error(`${client.name} is not authenticated`);
            }

            const chunks = await database.getChunkTimestamps(
                packet.dimension,
                packet.regions,
            );
            if (chunks.length) {
                await client.send({ type: "Catchup", dimension: packet.dimension, chunks });
            }
        }
    });
});
