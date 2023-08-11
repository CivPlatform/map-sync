export interface CatchupChunk {
	world: string
	chunk_x: number
	chunk_z: number
	ts: number
}

export interface Pos2D {
	readonly x: number;
	readonly z: number;
}
