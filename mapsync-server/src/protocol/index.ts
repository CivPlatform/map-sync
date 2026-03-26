import { BufferReader, BufferWriter } from "./buffers";
import type { CatchupChunk } from "../model";
import { SHA1_HASH_LENGTH } from "../constants";
import {
    asInt32,
    asUnt8,
    int16,
    int32,
    int64,
    unt16,
    unt8,
} from "../deps/ints";

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
    protected constructor(public readonly packetId: unt8) {}

    public get name(): string {
        return this.constructor.name ?? `Packet[${this.packetId}]`;
    }
}

export class ServerboundHandshakePacket extends Packet {
    public static readonly PACKET_ID = asUnt8(1);

    public constructor(
        public readonly modVersion: string,
        public readonly mojangName: string,
        public readonly gameAddress: string,
        public readonly dimension: string,
    ) {
        super(ServerboundHandshakePacket.PACKET_ID);
    }

    public static decode(reader: BufferReader): ServerboundHandshakePacket {
        return new ServerboundHandshakePacket(
            reader.readString(),
            reader.readString(),
            reader.readString(),
            reader.readString(),
        );
    }
}

export class ClientboundEncryptionRequestPacket extends Packet {
    public static readonly PACKET_ID = asUnt8(2);

    public constructor(
        public readonly publicKey: Buffer,
        public readonly verifyToken: Buffer,
    ) {
        super(ClientboundEncryptionRequestPacket.PACKET_ID);
    }

    public encode(writer: BufferWriter) {
        writer.writeLengthPrefixedBytes(
            BufferWriter.prototype.writeUnt16,
            this.publicKey,
        );
        writer.writeLengthPrefixedBytes(
            BufferWriter.prototype.writeUnt8,
            this.verifyToken,
        );
    }
}

export class ServerboundEncryptionResponsePacket extends Packet {
    public static readonly PACKET_ID = asUnt8(3);

    public constructor(
        /** encrypted with server's public key */
        public readonly sharedSecret: Buffer,
        /** encrypted with server's public key */
        public readonly verifyToken: Buffer,
    ) {
        super(ServerboundEncryptionResponsePacket.PACKET_ID);
    }

    public static decode(
        reader: BufferReader,
    ): ServerboundEncryptionResponsePacket {
        return new ServerboundEncryptionResponsePacket(
            reader.readBytesOfLength(reader.readUnt8()),
            reader.readBytesOfLength(reader.readUnt8()),
        );
    }
}

export class ClientboundRegionTimestampsPacket extends Packet {
    public static readonly PACKET_ID = asUnt8(7);

    public constructor(
        public readonly dimension: string,
        public readonly regionX: int16,
        public readonly regionZ: int16,
        public readonly timestamp: int64,
    ) {
        super(ClientboundRegionTimestampsPacket.PACKET_ID);
    }

    public encode(writer: BufferWriter) {
        writer.writeString(this.dimension);
        writer.writeInt16(this.regionX);
        writer.writeInt16(this.regionZ);
        writer.writeInt64(this.timestamp);
    }
}

export class ServerboundChunkTimestampsRequestPacket extends Packet {
    public static readonly PACKET_ID = asUnt8(8);

    public constructor(
        public readonly dimension: string,
        public readonly regionX: int16,
        public readonly regionZ: int16,
    ) {
        super(ServerboundChunkTimestampsRequestPacket.PACKET_ID);
    }

    public static decode(
        reader: BufferReader,
    ): ServerboundChunkTimestampsRequestPacket {
        return new ServerboundChunkTimestampsRequestPacket(
            reader.readString(),
            reader.readInt16(),
            reader.readInt16(),
        );
    }
}

export class ClientboundChunkTimestampsResponsePacket extends Packet {
    public static readonly PACKET_ID = asUnt8(5);

    public constructor(
        public readonly dimension: string,
        public readonly regionX: int16,
        public readonly regionZ: int16,
        public readonly chunks: CatchupChunk[],
    ) {
        super(ClientboundChunkTimestampsResponsePacket.PACKET_ID);
        switch (true) {
            case this.chunks.length < 1:
                throw new Error(`Catchup chunks must not be empty`);
            case this.chunks.length > 1024:
                throw new Error(`Catchup chunks contains too many chunks!`);
        }
    }

    public encode(writer: BufferWriter) {
        writer.writeString(this.dimension);
        writer.writeInt16(this.regionX);
        writer.writeInt16(this.regionZ);
        writer.writeUnt10(this.chunks.length);
        for (const row of this.chunks) {
            writer.writeUnt5(row.chunkX & 31n);
            writer.writeUnt5(row.chunkZ & 31n);
            writer.writeInt64(row.timestamp);
        }
    }
}

export class ServerboundCatchupRequestPacket extends Packet {
    public static readonly PACKET_ID = asUnt8(6);

    public constructor(
        public readonly dimension: string,
        public readonly chunks: CatchupChunk[],
    ) {
        super(ServerboundCatchupRequestPacket.PACKET_ID);
    }

    public static decode(
        reader: BufferReader,
    ): ServerboundCatchupRequestPacket {
        const dimension = reader.readString();
        const anchorChunkX = reader.readInt16() << 5n;
        const anchorChunkZ = reader.readInt16() << 5n;
        const chunks: CatchupChunk[] = new Array(Number(reader.readUnt10()));
        for (let i = 0; i < chunks.length; i++) {
            chunks[i] = {
                chunkX: asInt32(anchorChunkX + reader.readUnt5()),
                chunkZ: asInt32(anchorChunkZ + reader.readUnt5()),
                timestamp: reader.readInt64(),
            };
        }
        return new ServerboundCatchupRequestPacket(dimension, chunks);
    }
}

export class ChunkTilePacket extends Packet {
    public static readonly PACKET_ID = asUnt8(4);

    public constructor(
        public readonly dimension: string,
        public readonly chunkX: int32,
        public readonly chunkZ: int32,
        public readonly timestamp: int64,
        public readonly dataVersion: unt16,
        public readonly dataHash: Buffer,
        public readonly data: Buffer,
    ) {
        super(ChunkTilePacket.PACKET_ID);
    }

    public static decode(reader: BufferReader): ChunkTilePacket {
        return new ChunkTilePacket(
            reader.readString(),
            reader.readInt32(),
            reader.readInt32(),
            reader.readInt64(),
            reader.readUnt16(),
            reader.readBytesOfLength(SHA1_HASH_LENGTH),
            reader.readRemainder(),
        );
    }

    public encode(writer: BufferWriter) {
        writer.writeString(this.dimension);
        writer.writeInt32(this.chunkX);
        writer.writeInt32(this.chunkZ);
        writer.writeInt64(this.timestamp);
        writer.writeUnt16(this.dataVersion);
        writer.writeBytes(this.dataHash);
        writer.writeBytes(this.data); // XXX do we need to prefix with length?
    }
}

export function decodePacket(reader: BufferReader): ServerboundPacket {
    const packetId: unt8 = reader.readUnt8();
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

export function encodePacket(
    pkt: ClientboundPacket,
    writer: BufferWriter,
): void {
    writer.writeUnt8(pkt.packetId);
    pkt.encode(writer);
}
