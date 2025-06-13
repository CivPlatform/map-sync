import { BufWriter } from "./BufWriter";
import { CatchupRegion } from "../model";

export interface RegionTimestampsPacket {
    type: "RegionTimestamps";
    world: string;
    region: CatchupRegion;
}

export namespace RegionTimestampsPacket {
    export function encode(packet: RegionTimestampsPacket, writer: BufWriter) {
        writer.writeString(packet.world);
        console.log(`Sending region for [${packet.world}]`, packet);
        writer.writeInt16(packet.region.regionX);
        writer.writeInt16(packet.region.regionZ);
        writer.writeInt64(packet.region.timestamp);
    }
}
