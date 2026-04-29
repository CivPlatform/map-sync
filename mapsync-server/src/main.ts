import node_crypto from "crypto";
import node_utils from "node:util";
import { setServer } from "./cli.ts";
import * as database from "./database.ts";
import * as metadata from "./metadata.ts";
import { WSServer, WSClient } from "./server.ts";
import {
    ChunkTilePacket,
    ClientboundChunkTimestampsResponsePacket,
    ClientboundRegionTimestampsPacket,
    ServerboundCatchupRequestPacket,
    ServerboundChunkTimestampsRequestPacket,
    ServerboundIdentityResponsePacket,
    ServerboundHandshakePacket,
    type ServerboundPacket,
    ClientboundWelcomePacket,
    UnexpectedPacketError,
    ClientboundIdentityRequestPacket,
    ServerboundDimensionChangePacket,
} from "./packets.ts";
import {
    AwaitingHandshake,
    AwaitingIdentityResponse,
    Welcomed,
} from "./auth.ts";
import { SUPPORTED_VERSIONS } from "./constants.ts";
import { createOfflineUuid } from "./deps/uuid.ts";

let config: metadata.Config = null!;
let main: ProtocolHandler = null!;
Promise.resolve().then(async () => {
    await database.setup();

    config = metadata.getConfig();

    // These two are only used if whitelist is enabled... but best to load them
    // anyway lest there be a modification to them that is then saved.
    await metadata.loadWhitelist();
    await metadata.loadUuidCache();

    main = new ProtocolHandler(config);
    setServer(main.server);
});

export class ProtocolHandler {
    public readonly server: WSServer;

    public constructor(private readonly config: metadata.Config) {
        this.server = new WSServer(config, this);
    }

    public async handleClientConnected(client: WSClient) {
        if (client.auth !== null) {
            throw new Error("Client has already started authing!");
        }
        client.auth = new AwaitingHandshake();
    }

    public async handleClientDisconnected(client: WSClient) {
        client.auth = null;
    }

    public async handleClientPacketReceived(
        client: WSClient,
        pkt: ServerboundPacket,
    ) {
        switch (true) {
            case pkt instanceof ServerboundHandshakePacket:
                return this.handleHandshake(client, pkt);
            case pkt instanceof ServerboundIdentityResponsePacket:
                return this.handleIdentityResponse(client, pkt);
            case pkt instanceof ServerboundChunkTimestampsRequestPacket:
                return this.handleChunkTimestampsRequest(client, pkt);
            case pkt instanceof ServerboundCatchupRequestPacket:
                return this.handleCatchupRequest(client, pkt);
            case pkt instanceof ChunkTilePacket:
                return this.handleChunkTilePacket(client, pkt);
            case pkt instanceof ServerboundDimensionChangePacket:
                return this.handleDimensionChange(client, pkt);
            default:
                throw new Error(
                    `Unknown packet [${node_utils.inspect(pkt)}] from client ${client.id}`,
                );
        }
    }

    private async handleHandshake(
        client: WSClient,
        packet: ServerboundHandshakePacket,
    ) {
        if (!(client.auth instanceof AwaitingHandshake)) {
            throw new UnexpectedPacketError(packet);
        }
        if (!SUPPORTED_VERSIONS.has(packet.modVersion)) {
            throw new Error(
                `Connected with unsupported version [${packet.modVersion}]`,
            );
        }
        // TODO: Check whether the game address is supported
        client.gameAddress = packet.gameAddress;
        const serverSalt: Buffer = this.config.auth
            ? node_crypto.randomBytes(32)
            : Buffer.allocUnsafe(0);
        client.auth = new AwaitingIdentityResponse(serverSalt);
        client.send(new ClientboundIdentityRequestPacket(serverSalt));
    }

    private async handleIdentityResponse(
        client: WSClient,
        packet: ServerboundIdentityResponsePacket,
    ) {
        if (!(client.auth instanceof AwaitingIdentityResponse)) {
            throw new UnexpectedPacketError(packet);
        }
        if (this.config.auth) {
            if (packet.clientSalt.length === 0) {
                throw new Error(
                    "Client sent an empty clientSalt despite being required to auth!",
                );
            }
            const serverIdHex = node_crypto
                .createHash("sha1")
                .update(client.auth.serverSalt)
                .update(packet.clientSalt)
                .digest()
                .toString("hex");
            client.auth = await fetch(
                `https://sessionserver.mojang.com/session/minecraft/hasJoined?username=${packet.claimedUsername}&serverId=${serverIdHex}`,
            )
                .then(async (res) => {
                    if (res.status === 204) {
                        throw new Error(
                            `Failed to authenticate as [${packet.claimedUsername}]!`,
                        );
                    }
                    return res.json();
                })
                .then((json: any) => ({
                    name: json.name as string,
                    uuid: (json.id as string).replace(
                        /^(........)-?(....)-?(....)-?(....)-?(............)$/,
                        "$1-$2-$3-$4-$5",
                    ),
                }))
                .then((res) => new Welcomed(res.name, res.uuid, true));
        } else {
            if (packet.clientSalt.length !== 0) {
                throw new Error(
                    "Client sent a non-empty clientSalt despite no auth!",
                );
            }
            client.auth = new Welcomed(
                packet.claimedUsername,
                createOfflineUuid(packet.claimedUsername),
                false,
            );
        }
        await this.handleClientAuthenticated(client);
    }

    private async handleClientAuthenticated(client: WSClient) {
        const welcome = client.requireWelcomed();

        if (welcome.authed) {
            metadata.cachePlayerUuid(welcome.name, welcome.uuid);
            await metadata.saveUuidCache();
        }

        if (config.whitelist && !metadata.whitelist.has(welcome.uuid)) {
            client.kick(`Not whitelisted`);
            return;
        }

        // TODO check version, mc server, user access

        client.send(new ClientboundWelcomePacket());
    }

    private async handleDimensionChange(
        client: WSClient,
        pkt: ServerboundDimensionChangePacket,
    ) {
        if (client.isInDimension(pkt.dimension)) {
            return;
        }
        // TODO: Stop any sync process of the previous dimension

        client.dimension = pkt.dimension;

        for (const region of await database.getRegionTimestamps(
            client.dimension!,
        )) {
            client.send(
                new ClientboundRegionTimestampsPacket(
                    client.dimension!,
                    region.regionX,
                    region.regionZ,
                    region.timestamp,
                ),
            );
        }
    }

    private async handleChunkTilePacket(
        client: WSClient,
        pkt: ChunkTilePacket,
    ) {
        const welcome = client.requireWelcomed();

        if (!client.isInDimension(pkt.dimension)) {
            client.warn(
                `Client send chunk data for [${pkt.dimension}] when their dimension is [${client.dimension}]!`,
            );
            return;
        }

        // TODO ignore if same chunk hash exists in db

        await database
            .storeChunkData(
                pkt.dimension,
                pkt.chunkX,
                pkt.chunkZ,
                welcome.uuid,
                pkt.timestamp,
                pkt.dataVersion,
                pkt.dataHash,
                pkt.data,
            )
            .catch(console.error);

        // TODO small timeout, then skip if other client already has it
        for (const otherClient of this.server.clients.values()) {
            if (
                client === otherClient ||
                !(otherClient.auth instanceof Welcomed)
            )
                continue;
            otherClient.send(pkt);
        }
    }

    private async handleCatchupRequest(
        client: WSClient,
        pkt: ServerboundCatchupRequestPacket,
    ) {
        const welcome = client.requireWelcomed();

        if (!client.isInDimension(pkt.dimension)) {
            client.warn(
                `Client requested catchup for [${pkt.dimension}] when their dimension is [${client.dimension}]!`,
            );
            return;
        }

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

    private async handleChunkTimestampsRequest(
        client: WSClient,
        pkt: ServerboundChunkTimestampsRequestPacket,
    ) {
        const welcome = client.requireWelcomed();

        if (!client.isInDimension(pkt.dimension)) {
            client.warn(
                `Client requested chunk timestamps for [${pkt.dimension}] when their dimension is [${client.dimension}]!`,
            );
            return;
        }

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
