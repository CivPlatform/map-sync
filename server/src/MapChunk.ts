import {
	BaseEntity,
	Column,
	Entity,
	JoinColumn,
	ManyToOne,
	PrimaryColumn,
} from 'typeorm'
import { registerEntity } from './db'

export interface PlayerChunk {
	world: string
	chunk_x: number
	chunk_z: number
	uuid: string
	ts: number
	data: ChunkData
}

export interface ChunkData {
	hash: string
	version: number
	data: Buffer
}

/** share same data of a chunk between multiple PlayerChunk (different players) */
@Entity({ name: 'chunk_data' })
export class ChunkDataDB extends BaseEntity implements ChunkData {
	static primaryCols = ['hash']

	@PrimaryColumn({ type: 'text' })
	hash!: string

	@Column({ type: 'integer' })
	version!: number

	/** binary representation of map data */
	@Column({ type: 'blob' })
	data!: Buffer
}

registerEntity(ChunkDataDB)

/** A chunk as the player saw it at that time.
 * DB stores the one most recent entry per player. */
@Entity({ name: 'player_chunk' })
export class PlayerChunkDB extends BaseEntity implements PlayerChunk {
	static primaryCols = ['world', 'chunk_x', 'chunk_z', 'uuid']

	@PrimaryColumn({ type: 'text' })
	world!: string

	@PrimaryColumn({ type: 'integer' })
	chunk_x!: number

	@PrimaryColumn({ type: 'integer' })
	chunk_z!: number

	/** player who sent this chunk */
	@PrimaryColumn({ type: 'text' })
	uuid!: string

	@Column({ type: 'bigint' })
	ts!: number

	@ManyToOne(() => ChunkDataDB)
	@JoinColumn({ name: 'hash' })
	data!: ChunkData

	static async store(map_chunk: PlayerChunk) {
		// TODO if PlayerChunk exists, and holds last reference to old hash, delete ChunkData at old hash
		await ChunkDataDB.upsert(map_chunk.data, {
			conflictPaths: ChunkDataDB.primaryCols,
			skipUpdateIfNoValuesChanged: true,
		})
		await PlayerChunkDB.upsert(map_chunk, PlayerChunkDB.primaryCols)
	}
}

registerEntity(PlayerChunkDB)
