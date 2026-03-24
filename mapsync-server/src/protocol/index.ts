import { BufReader } from "./BufReader";
import { BufWriter } from "./BufWriter";
import type { CatchupChunk } from "../model";
import { SHA1_HASH_LENGTH } from "../constants";

export type ServerboundPacket =
    | ServerboundHandshakePacket
    | ServerboundEncryptionResponsePacket
    | ServerboundChunkTimestampsRequestPacket
    | ServerboundCatchupRequestPacket
    | ChunkTilePacket;

export type ClientboundPacket =
    | ClientboundEncryptionRequestPacket
    | ClientboundRegionTimestampsPacket
    | ClientboundChunkTimestampsResponsePacket
    | ChunkTilePacket;

abstract class Packet {
    protected constructor(public readonly packetId: number) {}

    public get name(): string {
        return this.constructor.name ?? `Packet[${this.packetId}]`;
    }
}

export class ServerboundHandshakePacket extends Packet {
    public static readonly PACKET_ID = 1;

    public constructor(
        public readonly modVersion: string,
        public readonly mojangName: string,
        public readonly gameAddress: string,
        public readonly world: string,
    ) {
        super(ServerboundHandshakePacket.PACKET_ID);
    }

    public static decode(reader: BufReader): ServerboundHandshakePacket {
        return new ServerboundHandshakePacket(
            reader.readString(),
            reader.readString(),
            reader.readString(),
            reader.readString(),
        );
    }
}

export class ClientboundEncryptionRequestPacket extends Packet {
    public static readonly PACKET_ID = 2;

    public constructor(
        public readonly publicKey: Buffer,
        public readonly verifyToken: Buffer,
    ) {
        super(ClientboundEncryptionRequestPacket.PACKET_ID);
    }

    public encode(writer: BufWriter) {
        writer.writeBufWithLen(this.publicKey);
        writer.writeBufWithLen(this.verifyToken);
    }
}

export class ServerboundEncryptionResponsePacket extends Packet {
    public static readonly PACKET_ID = 3;

    public constructor(
        /** encrypted with server's public key */
        public readonly sharedSecret: Buffer,
        /** encrypted with server's public key */
        public readonly verifyToken: Buffer,
    ) {
        super(ServerboundEncryptionResponsePacket.PACKET_ID);
    }

    public static decode(
        reader: BufReader,
    ): ServerboundEncryptionResponsePacket {
        return new ServerboundEncryptionResponsePacket(
            reader.readBufWithLen(),
            reader.readBufWithLen(),
        );
    }
}

export class ClientboundRegionTimestampsPacket extends Packet {
    public static readonly PACKET_ID = 7;

    public constructor(
        public readonly world: string,
        public readonly regionX: number,
        public readonly regionZ: number,
        public readonly timestamp: number,
    ) {
        super(ClientboundRegionTimestampsPacket.PACKET_ID);
    }

    public encode(writer: BufWriter) {
        writer.writeString(this.world);
        console.log(`Sending region for [${this.world}]`, this);
        writer.writeInt16(this.regionX);
        writer.writeInt16(this.regionZ);
        writer.writeInt64(this.timestamp);
    }
}

export class ServerboundChunkTimestampsRequestPacket extends Packet {
    public static readonly PACKET_ID = 8;

    public constructor(
        public readonly world: string,
        public readonly regionX: number,
        public readonly regionZ: number,
    ) {
        super(ServerboundChunkTimestampsRequestPacket.PACKET_ID);
    }

    public static decode(
        reader: BufReader,
    ): ServerboundChunkTimestampsRequestPacket {
        return new ServerboundChunkTimestampsRequestPacket(
            reader.readString(),
            reader.readInt16(),
            reader.readInt16(),
        );
    }
}

export class ClientboundChunkTimestampsResponsePacket extends Packet {
    public static readonly PACKET_ID = 5;

    public constructor(
        public readonly world: string,
        public readonly chunks: CatchupChunk[],
    ) {
        super(ClientboundChunkTimestampsResponsePacket.PACKET_ID);
        if (this.chunks.length < 1)
            throw new Error(`Catchup chunks must not be empty`);
    }

    public encode(writer: BufWriter) {
        writer.writeString(this.world);
        writer.writeUInt32(this.chunks.length);
        for (const row of this.chunks) {
            writer.writeInt32(row.chunkX);
            writer.writeInt32(row.chunkZ);
            writer.writeInt64(row.timestamp);
        }
    }
}

export class ServerboundCatchupRequestPacket extends Packet {
    public static readonly PACKET_ID = 6;

    public constructor(
        public readonly dimension: string,
        public readonly chunks: CatchupChunk[],
    ) {
        super(ServerboundCatchupRequestPacket.PACKET_ID);
    }

    public static decode(reader: BufReader): ServerboundCatchupRequestPacket {
        const dimension = reader.readString();
        const chunks: CatchupChunk[] = new Array(reader.readUInt32());
        for (let i = 0; i < chunks.length; i++) {
            chunks[i] = {
                chunkX: reader.readInt32(),
                chunkZ: reader.readInt32(),
                timestamp: reader.readInt64(),
            };
        }
        return new ServerboundCatchupRequestPacket(dimension, chunks);
    }
}

export class ChunkTilePacket extends Packet {
    public static readonly PACKET_ID = 4;

    public constructor(
        public readonly dimension: string,
        public readonly chunkX: number,
        public readonly chunkZ: number,
        public readonly timestamp: number,
        public readonly dataVersion: number,
        public readonly dataHash: Buffer,
        public readonly data: Buffer,
    ) {
        super(ChunkTilePacket.PACKET_ID);
    }

    public static decode(reader: BufReader): ChunkTilePacket {
        return new ChunkTilePacket(
            reader.readString(),
            reader.readInt32(),
            reader.readInt32(),
            reader.readInt64(),
            reader.readUInt16(),
            reader.readBufLen(SHA1_HASH_LENGTH),
            reader.readRemainder(),
        );
    }

    public encode(writer: BufWriter) {
        writer.writeString(this.dimension);
        writer.writeInt32(this.chunkX);
        writer.writeInt32(this.chunkZ);
        writer.writeInt64(this.timestamp);
        writer.writeUInt16(this.dataVersion);
        writer.writeBufRaw(this.dataHash);
        writer.writeBufRaw(this.data); // XXX do we need to prefix with length?
    }
}

export function decodePacket(reader: BufReader): ServerboundPacket {
    const packetId = reader.readUInt8();
    switch (packetId) {
        case ServerboundHandshakePacket.PACKET_ID:
            return ServerboundHandshakePacket.decode(reader);
        case ServerboundEncryptionResponsePacket.PACKET_ID:
            return ServerboundEncryptionResponsePacket.decode(reader);
        case ServerboundChunkTimestampsRequestPacket.PACKET_ID:
            return ServerboundChunkTimestampsRequestPacket.decode(reader);
        case ServerboundCatchupRequestPacket.PACKET_ID:
            return ServerboundCatchupRequestPacket.decode(reader);
        case ChunkTilePacket.PACKET_ID:
            return ChunkTilePacket.decode(reader);
        default:
            throw new Error(`Unknown packet type ${packetId}`);
    }
}

export function encodePacket(pkt: ClientboundPacket, writer: BufWriter): void {
    writer.writeUInt8(pkt.packetId);
    pkt.encode(writer);
}
