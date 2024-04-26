import { BufReader } from "./BufReader";
import { BufWriter } from "./BufWriter";
import { SHA1_HASH_LENGTH } from "../constants";

export interface ChunkTilePacket {
    type: "ChunkTile";
    world: string;
    chunk_x: number;
    chunk_z: number;
    ts: number;
    data: { version: number; hash: Buffer; data: Buffer };
}

export namespace ChunkTilePacket {
    export function decode(reader: BufReader): ChunkTilePacket {
        return {
            type: "ChunkTile",
            world: reader.readString(),
            chunk_x: reader.readInt32(),
            chunk_z: reader.readInt32(),
            ts: reader.readUInt64(),
            data: {
                version: reader.readUInt16(),
                hash: reader.readBufLen(SHA1_HASH_LENGTH),
                data: reader.readRemainder(),
            },
        };
    }

    export function encode(pkt: ChunkTilePacket, writer: BufWriter) {
        writer.writeString(pkt.world);
        writer.writeInt32(pkt.chunk_x);
        writer.writeInt32(pkt.chunk_z);
        writer.writeUInt64(pkt.ts);
        writer.writeUInt16(pkt.data.version);
        writer.writeBufRaw(pkt.data.hash);
        writer.writeBufRaw(pkt.data.data); // XXX do we need to prefix with length?
    }
}
