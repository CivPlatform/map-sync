export interface RegionPos {
    readonly x: number,
    readonly z: number
}

export interface RegionTimestamp extends RegionPos {
    readonly ts: number // TODO: Change this to a bigint at some point
}

export interface CatchupChunk {
    world: string;
    chunk_x: number;
    chunk_z: number;
    ts: number;
}
