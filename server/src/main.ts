import "./cli";
import * as database from "./database";
import * as metadata from "./metadata";
import { type ClientPacket } from "./protocol";
import { type ProtocolHandler, TcpClient, TcpServer } from "./server";
import {
    ChunkTilePacket,
    ClientboundChunkTimestampsResponsePacket,
    ClientboundRegionTimestampsPacket,
    ServerboundCatchupRequestPacket,
    ServerboundChunkTimestampsRequestPacket,
} from "./protocol/packets.ts";

let config: metadata.Config = null!;
Promise.resolve().then(async () => {
    await database.setup();

    config = metadata.getConfig();

    // These two are only used if whitelist is enabled... but best to load them
    // anyway lest there be a modification to them that is then saved.
    await metadata.loadWhitelist();
    await metadata.loadUuidCache();

    const server = new TcpServer(
        config.host,
        config.port,
        new (class implements ProtocolHandler {
            public async handleClientConnected(client: TcpClient) {}

            public async handleClientDisconnected(client: TcpClient) {}

            public async handleClientAuthenticated(client: TcpClient) {
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

                await client.send(
                    new ClientboundRegionTimestampsPacket(
                        client.world!,
                        await database.getRegionTimestamps(client.world!),
                    ),
                );
            }

            public async handleClientPacketReceived(
                client: TcpClient,
                packet: ClientPacket,
            ) {
                client.debug(client.mcName + " <- " + packet.type.toString());
                switch (packet.type) {
                    case ChunkTilePacket.TYPE:
                        return this.handleChunkTilePacket(
                            client,
                            packet as ChunkTilePacket,
                        );
                    case ServerboundCatchupRequestPacket.TYPE:
                        return this.handleCatchupRequest(
                            client,
                            packet as ServerboundCatchupRequestPacket,
                        );
                    case ServerboundChunkTimestampsRequestPacket.TYPE:
                        return this.handleRegionCatchupPacket(
                            client,
                            packet as ServerboundChunkTimestampsRequestPacket,
                        );
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
                packet: ChunkTilePacket,
            ) {
                if (!client.uuid) {
                    throw new Error(`${client.name} is not authenticated`);
                }

                // TODO ignore if same chunk hash exists in db

                await database
                    .storeChunkData(
                        packet.dimension,
                        packet.chunkX,
                        packet.chunkZ,
                        client.uuid!,
                        packet.timestamp,
                        packet.version,
                        packet.hash,
                        packet.data,
                    )
                    .catch(console.error);

                // TODO small timeout, then skip if other client already has it
                await Promise.allSettled(
                    Object.values(server.clients)
                        .filter(
                            (other) =>
                                other !== client &&
                                (other.uuid ?? null) !== null,
                        )
                        .map((other) => other.send(packet)),
                );

                // TODO queue tile render for web map
            }

            private async handleCatchupRequest(
                client: TcpClient,
                packet: ServerboundCatchupRequestPacket,
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
                        console.error(
                            `${client.name} requested unavailable chunk`,
                            {
                                world: packet.dimension,
                                ...req,
                            },
                        );
                        continue;
                    }

                    if (chunk.ts > req.timestamp) continue; // someone sent a new chunk, which presumably got relayed to the client
                    if (chunk.ts < req.timestamp) continue; // the client already has a chunk newer than this

                    await client.send(
                        new ChunkTilePacket(
                            packet.dimension,
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

            private async handleRegionCatchupPacket(
                client: TcpClient,
                packet: ServerboundChunkTimestampsRequestPacket,
            ) {
                if (!client.uuid) {
                    throw new Error(`${client.name} is not authenticated`);
                }

                const chunks = await database.getChunkTimestamps(
                    packet.dimension,
                    packet.regions.map((region) => ({
                        x: region.regionX,
                        z: region.regionZ,
                    })),
                );
                if (chunks.length) {
                    await client.send(
                        new ClientboundChunkTimestampsResponsePacket(
                            packet.dimension,
                            chunks,
                        ),
                    );
                }
            }
        })(),
    );
});
