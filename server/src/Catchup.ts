export interface CatchupChunk {
	world: string
	chunk_x: number
	chunk_z: number
	ts: bigint // timestamp in milliseconds
}
