import { int16, int32, int64, unt16 } from "./deps/ints";

export interface CatchupRegion {
    readonly regionX: int16;
    readonly regionZ: int16;
    readonly timestamp: int64;
}

export interface CatchupChunk {
    readonly chunkX: int32;
    readonly chunkZ: int32;
    readonly timestamp: int64;
}

export interface StoredChunk {
    version: unt16;
    timestamp: int64;
    hash: Buffer;
    data: Buffer;
}
