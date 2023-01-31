import { CatchupChunk } from "../Catchup";
import { BufReader } from "./BufReader";

export interface CatchupRequestPacket {
    type: "CatchupRequest";
    chunks: CatchupChunk[];
}

export namespace CatchupRequestPacket {
    export function decode(reader: BufReader): CatchupRequestPacket {
        const world = reader.readString();
        const numChunks = reader.readUInt32();
        const chunks: CatchupChunk[] = [];
        for (let i = 0; i < numChunks; i++) {
            chunks.push({
                world,
                chunk_x: reader.readInt32(),
                chunk_z: reader.readInt32(),
                ts: reader.readUInt64(),
            });
        }
        return { type: "CatchupRequest", chunks };
    }
}
