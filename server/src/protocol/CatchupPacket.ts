import { type CatchupChunk } from "../model";
import { BufWriter } from "./BufWriter";

export interface CatchupPacket {
    type: "Catchup";
    world: string;
    chunks: CatchupChunk[];
}

export namespace CatchupPacket {
    export function encode(pkt: CatchupPacket, writer: BufWriter) {
        if (pkt.chunks.length < 1)
            throw new Error(`Catchup chunks must not be empty`);
        writer.writeString(pkt.world);
        writer.writeUInt32(pkt.chunks.length);
        for (const row of pkt.chunks) {
            writer.writeInt32(row.chunkX);
            writer.writeInt32(row.chunkZ);
            writer.writeUInt64(row.timestamp);
        }
    }
}
