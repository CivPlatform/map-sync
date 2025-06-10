import { BufferWriter, BufferReader } from "./buffers.ts";
import { SHA1_HASH_LENGTH } from "../constants.ts";

interface Packet {
    type: Symbol;
}

function readArray<T>(length: number, parser: () => T): Array<T> {
    const array: T[] = new Array(length);
    for (let i = 0; i < length; i++) {
        array[i] = parser();
    }
    return array;
}

export class ServerboundHandshakePacket implements Packet {
    public static readonly TYPE = Symbol("ServerboundHandshakePacket");

    public readonly type = ServerboundHandshakePacket.TYPE;

    public constructor(
        public readonly modVersion: string,
        public readonly mojangName: string,
        public readonly gameAddress: string,
        public readonly dimension: string,
    ) {}

    public static decode(reader: BufferReader): ServerboundHandshakePacket {
        return new ServerboundHandshakePacket(
            reader.readString(),
            reader.readString(),
            reader.readString(),
            reader.readString(),
        );
    }
}

export class ClientboundEncryptionRequestPacket implements Packet {
    public static readonly TYPE = Symbol("ClientboundEncryptionRequestPacket");

    public readonly type = ClientboundEncryptionRequestPacket.TYPE;

    public constructor(
        public readonly publicKey: Buffer,
        public readonly verifyToken: Buffer,
    ) {}

    public encode(writer: BufferWriter) {
        writer.writeBufWithLen(this.publicKey);
        writer.writeBufWithLen(this.verifyToken);
    }
}

export class ServerboundEncryptionResponsePacket implements Packet {
    public static readonly TYPE = Symbol("ServerboundEncryptionResponsePacket");

    public readonly type = ServerboundEncryptionResponsePacket.TYPE;

    public constructor(
        public readonly sharedSecret: Buffer,
        public readonly verifyToken: Buffer,
    ) {}

    public static decode(
        reader: BufferReader,
    ): ServerboundEncryptionResponsePacket {
        return new ServerboundEncryptionResponsePacket(
            reader.readBufWithLen(),
            reader.readBufWithLen(),
        );
    }
}

export class ClientboundRegionTimestampsPacket implements Packet {
    public static readonly TYPE = Symbol("ClientboundRegionTimestampsPacket");

    public readonly type = ClientboundRegionTimestampsPacket.TYPE;

    public constructor(
        public readonly dimension: string,
        public readonly regions: Array<{
            readonly regionX: number;
            readonly regionZ: number;
            readonly timestamp: number;
        }>,
    ) {}

    public encode(writer: BufferWriter) {
        writer.writeString(this.dimension);
        writer.writeInt16(this.regions.length);
        for (const region of this.regions) {
            writer.writeInt16(region.regionX);
            writer.writeInt16(region.regionZ);
            writer.writeInt64(region.timestamp);
        }
    }
}

export class ServerboundChunkTimestampsRequestPacket implements Packet {
    public static readonly TYPE = Symbol(
        "ServerboundChunkTimestampsRequestPacket",
    );

    public readonly type = ServerboundChunkTimestampsRequestPacket.TYPE;

    public constructor(
        public readonly dimension: string,
        public readonly regions: Array<{
            readonly regionX: number;
            readonly regionZ: number;
        }>,
    ) {}

    public static decode(
        reader: BufferReader,
    ): ServerboundChunkTimestampsRequestPacket {
        return new ServerboundChunkTimestampsRequestPacket(
            reader.readString(),
            readArray(reader.readInt16(), () => ({
                regionX: reader.readInt16(),
                regionZ: reader.readInt16(),
            })),
        );
    }
}

export class ClientboundChunkTimestampsResponsePacket implements Packet {
    public static readonly TYPE = Symbol(
        "ClientboundChunkTimestampsResponsePacket",
    );

    public readonly type = ClientboundChunkTimestampsResponsePacket.TYPE;

    public constructor(
        public readonly dimension: string,
        public readonly chunks: Array<{
            readonly chunkX: number;
            readonly chunkZ: number;
            readonly timestamp: number;
        }>,
    ) {}

    public encode(writer: BufferWriter) {
        writer.writeString(this.dimension);
        writer.writeUnt32(this.chunks.length);
        for (const chunk of this.chunks) {
            writer.writeInt32(chunk.chunkX);
            writer.writeInt32(chunk.chunkZ);
            writer.writeUnt64(chunk.timestamp);
        }
    }
}

export class ServerboundCatchupRequestPacket implements Packet {
    public static readonly TYPE = Symbol("ServerboundCatchupRequestPacket");

    public readonly type = ServerboundCatchupRequestPacket.TYPE;

    public constructor(
        public readonly dimension: string,
        public readonly chunks: Array<{
            readonly chunkX: number;
            readonly chunkZ: number;
            readonly timestamp: number;
        }>,
    ) {}

    public static decode(
        reader: BufferReader,
    ): ServerboundCatchupRequestPacket {
        return new ServerboundCatchupRequestPacket(
            reader.readString(),
            readArray(reader.readUnt32(), () => ({
                chunkX: reader.readInt32(),
                chunkZ: reader.readInt32(),
                timestamp: reader.readUnt64(),
            })),
        );
    }
}

export class ChunkTilePacket implements Packet {
    public static readonly TYPE = Symbol("ChunkTilePacket");

    public readonly type = ChunkTilePacket.TYPE;

    public constructor(
        public readonly dimension: string,
        public readonly chunkX: number,
        public readonly chunkZ: number,
        public readonly timestamp: number,
        public readonly version: number,
        public readonly hash: Buffer,
        public readonly data: Buffer,
    ) {}

    public encode(writer: BufferWriter) {
        writer.writeString(this.dimension);
        writer.writeInt32(this.chunkX);
        writer.writeInt32(this.chunkZ);
        writer.writeUnt64(this.timestamp);
        writer.writeUnt16(this.version);
        writer.writeBufRaw(this.hash);
        writer.writeBufRaw(this.data); // XXX do we need to prefix with length?
    }

    public static decode(reader: BufferReader): ChunkTilePacket {
        return new ChunkTilePacket(
            reader.readString(),
            reader.readInt32(),
            reader.readInt32(),
            reader.readUnt64(),
            reader.readUnt16(),
            reader.readBufLen(SHA1_HASH_LENGTH),
            reader.readRemainder(),
        );
    }
}
