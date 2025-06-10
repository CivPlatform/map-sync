import { BufReader } from "./BufReader";
import { type Pos2D } from "../model";

export interface RegionCatchupPacket {
    type: "RegionCatchup";
    dimension: string;
    regions: Pos2D[];
}

export namespace RegionCatchupPacket {
    export function decode(reader: BufReader): RegionCatchupPacket {
        const dimension = reader.readString();
        const regions: Pos2D[] = new Array(reader.readInt16());
        for (let i = 0; i < regions.length; i++) {
            regions[i] = {
                x: reader.readInt16(),
                z: reader.readInt16(),
            };
        }
        return { type: "RegionCatchup", dimension, regions };
    }
}
