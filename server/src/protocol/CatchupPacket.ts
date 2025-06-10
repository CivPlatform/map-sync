import { type CatchupChunk } from "../model";
import { BufWriter } from "./BufWriter";

export interface CatchupPacket {
    type: "Catchup";
    dimension: string;
    chunks: CatchupChunk[];
}

export namespace CatchupPacket {
    export function encode(pkt: CatchupPacket, writer: BufWriter) {
        if (pkt.chunks.length < 1)
            throw new Error(`Catchup chunks must not be empty`);
        writer.writeString(pkt.dimension);
        writer.writeUnt32(pkt.chunks.length);
        for (const chunk of pkt.chunks) {
            writer.writeInt32(chunk.chunkX);
            writer.writeInt32(chunk.chunkZ);
            writer.writeUnt64(chunk.timestamp);
        }
    }
}
