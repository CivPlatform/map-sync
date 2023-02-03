import crypto from "node:crypto";
import net from "node:net";
import fetch from "node-fetch";
import type { ClientPacket, ServerPacket } from "../protocol";
import { decodePacket, encodePacket } from "../protocol";
import { BufReader } from "../protocol/BufReader";
import { BufWriter } from "../protocol/BufWriter";
import {
    ChunkCatchupRequestPacket,
    ChunkDataPacket,
    EncryptionRequestPacket,
    EncryptionResponsePacket,
    HandshakePacket,
    RegionCatchupRequestPacket,
    RegionCatchupResponsePacket,
    RegionTimestampsPacket
} from "../protocol/packets";
import * as encryption from "./encryption";
import { TcpServer } from "./server";
import { AbstractClientMode, UnsupportedPacketException } from "./mode";
import { getConfig, uuid_cache, uuid_cache_save, whitelist } from "../metadata";
import { PlayerChunk, PlayerChunkDB } from "../database/entities";
import { RegionTimestamp } from "../protocol/structs";

/** prevent Out of Memory when client sends a large packet */
const MAX_FRAME_SIZE = 2 ** 24;
let nextClientId = 1;

/** Prefixes packets with their length (UInt32BE);
 * handles Mojang authentication */
export class TcpClient {
    public readonly id = nextClientId++;
    /** contains mojang name once logged in */
    public name = "Client" + this.id;
    public mode: AbstractClientMode = null!;

    public uuid: string | undefined;
    public mcName: string | undefined;

    public constructor(
        public readonly socket: net.Socket,
        public readonly server: TcpServer
    ) {
        this.setStage0PreAuthMode();
        /** Accumulates received data, containing none, one, or multiple frames; the last frame may be partial only. */
        let receivedBuffer: Buffer = Buffer.alloc(0);
        socket.on("data", async (data: Buffer) => {
            try {
                data = await this.mode.postReceiveBufferTransformer(data);
                // creating a new buffer every time is fine in our case, because we expect most frames to be large
                receivedBuffer = Buffer.concat([receivedBuffer, data]);
                // we may receive multiple frames in one call
                while (true) {
                    if (receivedBuffer.length <= 4) {
                        // Back out of the "data" callback: we need to wait for more data
                        return;
                    }
                    const frameSize = receivedBuffer.readUInt32BE();
                    // TODO: Should this also check the size of the buffer itself?
                    // prevent Out of Memory
                    if (frameSize > MAX_FRAME_SIZE) {
                        return this.kick(
                            "Frame too large: " +
                                frameSize +
                                " have " +
                                receivedBuffer.length
                        );
                    }
                    if (receivedBuffer.length < 4 + frameSize) {
                        // wait for more data
                        return;
                    }
                    const frameReader = new BufReader(receivedBuffer);
                    frameReader.readUInt32(); // skip frame size
                    let packetBuffer = frameReader.readBufLen(frameSize);
                    receivedBuffer = frameReader.readRemainder();
                    const reader = new BufReader(packetBuffer);
                    try {
                        const packet = decodePacket(reader);
                        this.debug(`MapSync ← Client[${packet.type}]`);
                        await this.mode.onPacketReceived(packet);
                    } catch (err) {
                        this.warn(err);
                        this.kick("Error in packet handler");
                        return;
                    }
                }
            } catch (err) {
                this.warn(err);
                this.kick("Error in data handler");
                return;
            }
        });
        socket.on("close", (hadError: boolean) => {
            this.log("Closed.", { hadError });
        });
        socket.on("end", () => {
            this.log("Ended");
        });
        socket.on("timeout", () => {
            this.warn("Timeout");
        });
        socket.on("error", (err: Error) => {
            this.warn("Error:", err);
            this.kick("Socket error");
        });
    }

    private setStage0PreAuthMode() {
        const client = this;
        this.mode = new (class Stage0PreAuthMode extends AbstractClientMode {
            async onPacketReceived(packet: ClientPacket) {
                if (packet instanceof HandshakePacket) {
                    // TODO: Uncomment this when the version is correctly
                    //       filtered. Currently, the "packet.modVersion" will
                    //       return "${version}+fabric" or similar.
                    // if (packet.modVersion !== MOD_VERSION) {
                    //     client.kick(`Unsupported mod version [${packet.modVersion}]`);
                    //     return;
                    // }
                    if (packet.gameAddress !== getConfig().gameAddress) {
                        client.kick(
                            `Kicking for unsupported mod version [${packet.modVersion}]`
                        );
                        return;
                    }
                    const verifyToken = crypto.randomBytes(4);
                    client.setStage1PreAuthMode(
                        packet.mojangName,
                        verifyToken,
                        packet.world
                    );
                    await client.send(
                        new EncryptionRequestPacket(
                            encryption.PUBLIC_KEY_BUFFER,
                            verifyToken
                        )
                    );
                    return;
                }
                throw new UnsupportedPacketException(this, packet);
            }
        })();
    }

    private setStage1PreAuthMode(
        claimedUsername: string,
        verifyToken: Buffer,
        world: string
    ) {
        const client = this;
        this.mode = new (class Stage1PreAuthMode extends AbstractClientMode {
            async onPacketReceived(packet: ClientPacket) {
                if (packet instanceof EncryptionResponsePacket) {
                    const parsedVerifyToken = encryption.decrypt(
                        packet.verifyToken
                    );
                    if (!parsedVerifyToken.equals(verifyToken)) {
                        client.kick(
                            `Incorrect verifyToken! Expected [${verifyToken}], got [${parsedVerifyToken}]`
                        );
                        return;
                    }
                    const sharedSecret = encryption.decrypt(
                        packet.sharedSecret
                    );
                    const mojangAuth = await fetchHasJoined({
                        username: claimedUsername,
                        shaHex: encryption.generateShaHex(sharedSecret)
                    });
                    if (!mojangAuth?.uuid) {
                        client.kick(`Mojang auth failed`);
                        return;
                    }
                    client.log("Authenticated as", mojangAuth);
                    client.uuid = mojangAuth.uuid;
                    client.mcName = mojangAuth.name;
                    client.name += ":" + mojangAuth.name;
                    uuid_cache.set(mojangAuth.name, mojangAuth.uuid);
                    uuid_cache_save();
                    if (getConfig().whitelist) {
                        if (!whitelist.has(client.uuid)) {
                            client.log("Rejecting unwhitelisted user!");
                            client.kick(`Not whitelisted`);
                            return;
                        }
                    }
                    client.setPostAuthMode(
                        encryption.generateCiphers(sharedSecret)
                    );
                    const timestamps =
                        await PlayerChunkDB.getRegionTimestamps();
                    client.send(
                        new RegionTimestampsPacket(
                            world,
                            timestamps.map(
                                (timestamp) =>
                                    ({
                                        x: timestamp.region_x,
                                        z: timestamp.region_z,
                                        ts: timestamp.ts
                                    } as RegionTimestamp)
                            )
                        )
                    );
                    return;
                }
                throw new UnsupportedPacketException(this, packet);
            }
        })();
    }

    private setPostAuthMode(ciphers: encryption.Ciphers) {
        const client = this;
        this.mode = new (class PostAuthMode extends AbstractClientMode {
            async postReceiveBufferTransformer(
                buffer: Buffer
            ): Promise<Buffer> {
                return ciphers.decipher.update(buffer);
            }
            async preSendBufferTransformer(buffer: Buffer): Promise<Buffer> {
                return ciphers.encipher.update(buffer);
            }
            async onPacketReceived(packet: ClientPacket) {
                if (packet instanceof RegionCatchupRequestPacket) {
                    const chunks = await PlayerChunkDB.getCatchupData(
                        packet.world,
                        packet.regions
                            .map((region) => [region.x, region.z])
                            .flat()
                    );
                    if (chunks.length > 0) {
                        client.send(
                            new RegionCatchupResponsePacket(
                                packet.world,
                                chunks
                            )
                        );
                    }
                    return;
                }
                if (packet instanceof ChunkCatchupRequestPacket) {
                    for (const requestedChunk of packet.chunks) {
                        // TODO: This feels like it could be heavily optimised
                        //       with a raw batched query
                        let chunk = await PlayerChunkDB.getChunkWithData({
                            world: requestedChunk.world,
                            chunk_x: requestedChunk.chunk_x,
                            chunk_z: requestedChunk.chunk_z
                        });
                        if (!chunk) {
                            console.error(
                                `${client.name} requested unavailable chunk`,
                                chunk
                            );
                            continue;
                        }
                        if (chunk.ts > requestedChunk.ts) continue; // someone sent a new chunk, which presumably got relayed to the client
                        if (chunk.ts < requestedChunk.ts) continue; // the client already has a chunk newer than this
                        client.send(
                            new ChunkDataPacket(
                                chunk.world,
                                chunk.chunk_x,
                                chunk.chunk_z,
                                chunk.ts,
                                chunk.data
                            )
                        );
                    }
                    return;
                }
                if (packet instanceof ChunkDataPacket) {
                    // TODO ignore if same chunk hash exists in db
                    const playerChunk: PlayerChunk = {
                        world: packet.world,
                        chunk_x: packet.x,
                        chunk_z: packet.z,
                        uuid: client.uuid!,
                        ts: packet.timestamp,
                        data: packet.data
                    };
                    PlayerChunkDB.store(playerChunk).catch(console.error);
                    // TODO small timeout, then skip if other client already has it
                    for (const otherClient of client.server.clients.values()) {
                        if (client === otherClient) continue;
                        otherClient.send(packet);
                    }
                    // TODO queue tile render for web map
                    return;
                }
                throw new UnsupportedPacketException(this, packet);
            }
        })();
    }

    public kick(internalReason: string) {
        this.log(`Kicking:`, internalReason);
        this.socket.destroy();
    }

    public async send(pkt: ServerPacket) {
        this.debug(`MapSync[${pkt.type}] → Client`);
        const writer = new BufWriter(); // TODO size hint
        writer.writeUInt32(0); // set later, but reserve space in buffer
        encodePacket(pkt, writer);
        let buf = writer.getBuffer();
        buf.writeUInt32BE(buf.length - 4, 0); // write into space reserved above
        buf = await this.mode.preSendBufferTransformer(buf);
        this.socket.write(buf);
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

async function fetchHasJoined(args: {
    username: string;
    shaHex: string;
    clientIp?: string;
}) {
    const { username, shaHex, clientIp } = args;
    let url = `https://sessionserver.mojang.com/session/minecraft/hasJoined?username=${username}&serverId=${shaHex}`;
    if (clientIp) url += `&ip=${clientIp}`;
    const res = await fetch(url);
    try {
        if (res.status === 204) return null;
        let { id, name } = (await res.json()) as { id: string; name: string };
        const uuid = id.replace(
            /^(........)-?(....)-?(....)-?(....)-?(............)$/,
            "$1-$2-$3-$4-$5"
        );
        return { uuid, name };
    } catch (err) {
        console.error(res);
        throw err;
    }
}
