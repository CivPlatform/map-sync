import { BufReader } from "./BufReader";
import { Packets } from "./index";
import { BufWriter } from "./BufWriter";
import { CatchupChunk, Pos2D, Timestamped } from "./structs";

/**
 * The Minecraft client should send this packet IMMEDIATELY upon a successful
 * connection to the MapSync server.
 */
export class HandshakePacket {
    public readonly type: string = Packets[Packets.Handshake];

    /**
     * @param modVersion The MapSync version (effectively the protocol version)
     * @param mojangName The client's Mojang username (not their email)
     * @param gameAddress The server-address for the Minecraft server they're
     *                    connected to.
     * @param world The dimension the client is in.
     */
    public constructor(
        public readonly modVersion: string,
        public readonly mojangName: string,
        public readonly gameAddress: string,
        public readonly world: string
    ) {}

    public static decode(reader: BufReader): HandshakePacket {
        return new HandshakePacket(
            reader.readString(),
            reader.readString(),
            reader.readString(),
            reader.readString()
        );
    }
}

/**
 * This is sent back to the client after the Handshake.
 */
export class EncryptionRequestPacket {
    public readonly type = Packets[Packets.EncryptionRequest];

    /**
     * @param publicKey This server's public key.
     * @param verifyToken A transaction ID of four randomly generated bytes
     */
    public constructor(
        public readonly publicKey: Buffer,
        public readonly verifyToken: Buffer
    ) {}

    public encode(writer: BufWriter) {
        writer.writeBufWithLen(this.publicKey);
        writer.writeBufWithLen(this.verifyToken);
    }
}

/**
 * Once this packet is received, the client should be considered fully verified
 * and thus can share map information.
 */
export class EncryptionResponsePacket {
    public readonly type: string = Packets[Packets.EncryptionResponse];

    /**
     * @param sharedSecret A 16-bit secret created and shared by the client
     * @param verifyToken The verifyToken sent in the EncyrptionRequest which
     *                    has been encrypted with the server's public key.
     */
    public constructor(
        public readonly sharedSecret: Buffer,
        public readonly verifyToken: Buffer
    ) {}

    public static decode(reader: BufReader): EncryptionResponsePacket {
        return new EncryptionResponsePacket(
            reader.readBufWithLen(),
            reader.readBufWithLen()
        );
    }
}

/**
 * This is the first packet send to the client post-encryption setup. This
 * packet is used to inform the client when each region was last updated, which
 * the client can use to request newer regions from MapSync.
 */
export class RegionTimestampsPacket {
    public readonly type = Packets[Packets.RegionTimestamps];

    public constructor(
        public readonly world: string,
        public readonly regions: (Pos2D & Timestamped)[]
    ) {}

    public encode(writer: BufWriter) {
        writer.writeString(this.world);
        writer.writeInt16(this.regions.length);
        if (this.regions.length > 32767) {
            // TODO: Remove this if it's not an issue
            console.error(
                "Attempting to send region timestamps, but the regions surpass the maximum value for a signed-short length!"
            );
        }
        for (const region of this.regions) {
            writer.writeInt16(region.x);
            writer.writeInt16(region.z);
            writer.writeInt64(Number(region.timestamp)); // TODO: Make it bigint
        }
    }
}

/**
 * This is a response to the RegionTimestampsPacket: the client is requesting
 * to be updated on regions that are outdated for it.
 */
export class RegionCatchupRequestPacket {
    public readonly type: string = Packets[Packets.RegionCatchup];

    public constructor(
        public readonly world: string,
        public readonly regions: Pos2D[]
    ) {}

    public static decode(reader: BufReader): RegionCatchupRequestPacket {
        return new RegionCatchupRequestPacket(
            reader.readString(),
            (function (length) {
                const regions: Pos2D[] = new Array(length);
                for (let i = 0; i < length; i++) {
                    regions[i] = {
                        x: reader.readInt16(),
                        z: reader.readInt16()
                    };
                }
                return regions;
            })(reader.readInt16())
        );
    }
}

/**
 * This is a clarification packet. It responds to the request with all the
 * regions' internal chunk timestamps. That way the client doesn't need to
 * receive 32x32 chunk's worth of data if only a single chunk inside is newer
 * to the client.
 */
export class RegionCatchupResponsePacket {
    public readonly type = Packets[Packets.Catchup];

    public constructor(
        public readonly world: string,
        public readonly chunks: (Pos2D & Timestamped)[]
    ) {}

    public encode(writer: BufWriter) {
        writer.writeString(this.world);
        writer.writeUInt32(this.chunks.length);
        for (const region of this.chunks) {
            writer.writeInt32(region.x);
            writer.writeInt32(region.z);
            writer.writeUInt64(Number(region.timestamp)); // TODO: Make it bigint
        }
    }
}

/**
 * This is the final packet in the catchup request: it contains a list of all
 * the chunks the client wishes to receive.
 */
export class ChunkCatchupRequestPacket {
    public readonly type: string = Packets[Packets.CatchupRequest];

    public constructor(
        public readonly world: string,
        public readonly chunks: CatchupChunk[]
    ) {}

    public static decode(reader: BufReader): ChunkCatchupRequestPacket {
        const world = reader.readString();
        return new ChunkCatchupRequestPacket(
            world,
            (function (length) {
                const chunks: CatchupChunk[] = new Array(length);
                for (let i = 0; i < length; i++) {
                    chunks[i] = {
                        world: world,
                        chunk_x: reader.readInt32(),
                        chunk_z: reader.readInt32(),
                        ts: reader.readUInt64()
                    };
                }
                return chunks;
            })(reader.readUInt32())
        );
    }
}

/**
 * This is a bidirectional packet. It's used to fulfil chunk catchup requests,
 * but is also relayed verbatim to all other connected clients when new chunk
 * data is received.
 */
export class ChunkDataPacket {
    public readonly type = Packets[Packets.ChunkTile];

    public constructor(
        public readonly world: string,
        public readonly x: number,
        public readonly z: number,
        public readonly timestamp: number,
        public readonly version: number,
        public readonly hash: Buffer,
        public readonly data: Buffer
    ) {}

    public encode(writer: BufWriter) {
        writer.writeString(this.world);
        writer.writeInt32(this.x);
        writer.writeInt32(this.z);
        writer.writeUInt64(this.timestamp);
        writer.writeUInt16(this.version);
        writer.writeBufWithLen(this.hash);
        writer.writeBufRaw(this.data); // XXX do we need to prefix with length?
    }

    public static decode(reader: BufReader): ChunkDataPacket {
        return new ChunkDataPacket(
            reader.readString(),
            reader.readInt32(),
            reader.readInt32(),
            reader.readUInt64(),
            reader.readUInt16(),
            reader.readBufWithLen(),
            reader.readRemainder()
        );
    }
}
