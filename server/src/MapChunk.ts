import {
	BaseEntity,
	Column,
	Entity,
	JoinColumn,
	ManyToOne,
	PrimaryColumn,
} from 'typeorm'
import { registerEntity } from './db'
import {BufWriter} from "./protocol/BufWriter";

export interface PlayerChunk {
	world: string
	chunk_x: number
	chunk_z: number
	uuid: string
	ts: number
	data: ChunkData
}

export interface ChunkData {
	hash: Buffer
	version: number
	data: Buffer
}

/** share same data of a chunk between multiple PlayerChunk (different players) */
@Entity({ name: 'chunk_data' })
export class ChunkDataDB extends BaseEntity implements ChunkData {
	static primaryCols = ['hash']

	@PrimaryColumn({ type: 'blob' })
	hash!: Buffer

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

	static async getCatchupData(timestamp: number){
		let chunks = await PlayerChunkDB.createQueryBuilder()
			.where("player_chunk.ts >= :timestamp", {timestamp: timestamp})
			.orderBy("player_chunk.ts", "DESC")
			.getMany()

		let buf = new BufWriter();

		const seenChunks: Record<string, PlayerChunkDB> = {}
		for (const chunk of chunks) {
			const chunkPos = `${chunk.chunk_x},${chunk.chunk_z}`
			if (seenChunks[chunkPos]) continue
			seenChunks[chunkPos] = chunk
		}
		const chunksList = Object.values(seenChunks)
		buf.writeUInt32(chunksList.length)
		for(const row of chunksList){
			if(row){
				buf.writeString(row.world)
				buf.writeInt32(row.chunk_x)
				buf.writeInt32(row.chunk_z)
				buf.writeUInt64(row.ts)
			}
		}

		return buf.getBuffer();
	}
}

registerEntity(PlayerChunkDB)
