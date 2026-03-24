export interface CatchupRegion {
    readonly regionX: number;
    readonly regionZ: number;
    readonly timestamp: number;
}

export interface CatchupChunk {
    readonly chunkX: number;
    readonly chunkZ: number;
    readonly timestamp: number;
}
