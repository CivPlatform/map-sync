import crypto from "node:crypto";
import net from "node:net";
import util from "node:util";
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
import * as config from "../config/config";
import * as whitelist from "../config/whitelist";
import * as uuid_cache from "../config/uuid_cache";
import { getRegionTimestamps, getChunkTimestamps, getChunkData, storeChunkData } from "../database/data";

const PACKET_LOGGER = util.debuglog("packets");
/** prevent Out of Memory when client sends a large packet */
const MAX_FRAME_SIZE = 2 ** 24;
let nextClientId = 1n;

/** Prefixes packets with their length (UInt32BE);
 * handles Mojang authentication */
export class TcpClient {
    public readonly id = nextClientId++;
    /** contains mojang name once logged in */
    public name = "Client" + this.id;
    public mode: AbstractClientMode = null!;

    public uuid?: string;
    public username?: string;

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
                        PACKET_LOGGER(`MapSync ← ${this.name}[${packet.type}]`);
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
                    if (packet.gameAddress !== config.get().gameAddress) {
                        client.kick(
                            `Client not playing on the correct Minecraft server [${packet.gameAddress}]`
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
                    client.username = mojangAuth.name;
                    client.name += ":" + mojangAuth.name;
                    uuid_cache.entries.set(mojangAuth.name, mojangAuth.uuid);
                    uuid_cache.save();
                    if (config.get().whitelist) {
                        if (!whitelist.entries.has(client.uuid)) {
                            client.kick("Rejecting unwhitelisted user!");
                            return;
                        }
                    }
                    client.setPostAuthMode(
                        encryption.generateCiphers(sharedSecret)
                    );
                    client.send(new RegionTimestampsPacket(
                        world,
                        await getRegionTimestamps()
                    ));
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
                    const chunks = await getChunkTimestamps(
                        packet.world,
                        packet.regions
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
                        const chunk = await getChunkData(
                            packet.world,
                            requestedChunk.x,
                            requestedChunk.z,
                            requestedChunk.timestamp
                        );
                        if (!chunk) {
                            client.warn(
                                `Requested unavailable chunk! [${util.inspect(
                                    requestedChunk
                                )}]`
                            );
                            continue;
                        }
                        client.send(
                            new ChunkDataPacket(
                                packet.world,
                                requestedChunk.x,
                                requestedChunk.z,
                                requestedChunk.timestamp,
                                chunk.version,
                                chunk.hash,
                                chunk.data
                            )
                        );
                    }
                    return;
                }
                if (packet instanceof ChunkDataPacket) {
                    await storeChunkData(
                        packet.world,
                        packet.x,
                        packet.z,
                        client.uuid!,
                        packet.timestamp,
                        packet.hash,
                        packet.version,
                        packet.data
                    ).catch(console.error);
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
        PACKET_LOGGER(`MapSync[${pkt.type}] → ${this.name}`);
        const writer = new BufWriter(); // TODO size hint
        writer.writeUInt32(0); // set later, but reserve space in buffer
        encodePacket(pkt, writer);
        let buf = writer.getBuffer();
        buf.writeUInt32BE(buf.length - 4, 0); // write into space reserved above
        buf = await this.mode.preSendBufferTransformer(buf);
        this.socket.write(buf);
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
