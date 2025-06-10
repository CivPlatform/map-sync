import "./cli.ts";
import * as database from "./database.ts";
import * as metadata from "./metadata.ts";
import {
    type ClientPacket,
    encodePacketToBytes,
    UnexpectedPacket,
} from "./net/protocol.ts";
import { type ProtocolHandler, TcpClient, TcpServer } from "./net/server.ts";
import {
    ChunkTilePacket,
    ClientboundChunkTimestampsResponsePacket,
    ClientboundRegionTimestampsPacket,
    ServerboundCatchupRequestPacket,
    ServerboundChunkTimestampsRequestPacket,
} from "./net/packets.ts";
import { isAuthed, OnlineAuth, requireAuth } from "./net/auth.ts";

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
                if (client.auth instanceof OnlineAuth) {
                    metadata.cachePlayerUuid(
                        client.auth.name,
                        client.auth.uuid,
                    );
                    await metadata.saveUuidCache();

                    if (
                        config.whitelist &&
                        !metadata.whitelist.has(client.auth.uuid)
                    ) {
                        client.kick(
                            `Not whitelisted! [${Bun.inspect(client.auth)}]`,
                        );
                        return;
                    }
                }

                // TODO check version, mc server, user access

                await client.send(
                    new ClientboundRegionTimestampsPacket(
                        client.dimension!,
                        await database.getRegionTimestamps(client.dimension!),
                    ),
                );
            }

            public async handleClientPacketReceived(
                client: TcpClient,
                packet: ClientPacket,
            ) {
                switch (packet.type) {
                    case ChunkTilePacket.TYPE:
                        await this.handleChunkTilePacket(
                            client,
                            packet as ChunkTilePacket,
                        );
                        return;
                    case ServerboundCatchupRequestPacket.TYPE:
                        await this.handleCatchupRequest(
                            client,
                            packet as ServerboundCatchupRequestPacket,
                        );
                        return;
                    case ServerboundChunkTimestampsRequestPacket.TYPE:
                        await this.handleRegionCatchupPacket(
                            client,
                            packet as ServerboundChunkTimestampsRequestPacket,
                        );
                        return;
                    default:
                        throw new UnexpectedPacket(packet.type.toString());
                }
            }

            private async handleChunkTilePacket(
                client: TcpClient,
                packet: ChunkTilePacket,
            ) {
                requireAuth(client);

                // TODO ignore if same chunk hash exists in db

                if (client.auth instanceof OnlineAuth) {
                    await database
                        .storeChunkData(
                            packet.dimension,
                            packet.chunkX,
                            packet.chunkZ,
                            client.auth.uuid,
                            packet.timestamp,
                            packet.version,
                            packet.hash,
                            packet.data,
                        )
                        .catch(client.warn);
                }

                // TODO small timeout, then skip if other client already has it
                const packetRaw = encodePacketToBytes(packet);
                await Promise.allSettled(
                    server.clients
                        .values()
                        .filter((other) => other !== client && isAuthed(other))
                        .map((other) => other.sendRaw(packet.type, packetRaw)),
                );

                // TODO queue tile render for web map
            }

            private async handleCatchupRequest(
                client: TcpClient,
                packet: ServerboundCatchupRequestPacket,
            ) {
                requireAuth(client);

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
                requireAuth(client);

                const chunks = await database.getChunkTimestamps(
                    packet.dimension,
                    packet.regions.map((region) => ({
                        x: region.regionX,
                        z: region.regionZ,
                    })),
                );
                if (chunks.length > 0) {
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
