import { BufReader } from "./BufReader";
import { type Pos2D } from "../model";

export interface RegionCatchupPacket {
    type: "RegionCatchup";
    world: string;
    regions: Pos2D[];
}

export namespace RegionCatchupPacket {
    export function decode(reader: BufReader): RegionCatchupPacket {
        let world = reader.readString();
        const len = reader.readInt16();
        const regions: Pos2D[] = [];
        for (let i = 0; i < len; i++) {
            regions.push({
                x: reader.readInt16(),
                z: reader.readInt16(),
            });
        }
        return { type: "RegionCatchup", world, regions };
    }
}
