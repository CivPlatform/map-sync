export interface Pos2D {
    readonly x: number;
    readonly z: number;
}

export interface RegionTimestamp extends Pos2D {
    readonly ts: number; // TODO: Change this to a bigint at some point
}

export interface CatchupChunk {
    world: string;
    chunk_x: number;
    chunk_z: number;
    ts: number;
}
