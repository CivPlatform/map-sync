import { BufReader } from "./BufReader";

export interface RegionCatchupPacket {
    type: "RegionCatchup";
    world: string;
    regionX: number;
    regionZ: number;
}

export namespace RegionCatchupPacket {
    export function decode(reader: BufReader): RegionCatchupPacket {
        return {
            type: "RegionCatchup",
            world: reader.readString(),
            regionX: reader.readInt16(),
            regionZ: reader.readInt16(),
        };
    }
}
