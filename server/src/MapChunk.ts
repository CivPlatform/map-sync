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

	static async getRegionTimestamps() {
		// computing region coordinates in SQL requires truncating, not rounding
		return await PlayerChunkDB.query(`
			WITH region_real AS (SELECT
				chunk_x / 32.0 AS region_x_real,
				chunk_z / 32.0 AS region_z_real,
				ts
				FROM player_chunk
			) SELECT
				cast (region_x_real as int) - (region_x_real < cast (region_x_real as int)) AS region_x,
				cast (region_z_real as int) - (region_z_real < cast (region_z_real as int)) AS region_z,
				MAX(ts) AS ts
			FROM region_real
			GROUP BY region_x, region_z
			ORDER BY region_x DESC`)
		/*
		return await PlayerChunkDB.createQueryBuilder()
				.select("chunk_x / 32", "region_x")
				.addSelect("chunk_z / 32", "region_z")
				.addSelect("max(ts) as ts")
				.groupBy("region_x")
				.addGroupBy("region_z")
				.orderBy("region_x", "DESC")
				.getRawMany();
*/
	}

	static async getCatchupData(world: string, regions: number[]) {
		// TODO use TypeORM's API instead of building our own query string
		let regionsAsString: string[] = []
		let list: string = ''
		for (let i = 0; i < regions.length; i += 2) {
			regionsAsString.push('' + regions[i] + '_' + regions[i + 1] + '')
			if (i > 0) {
				list += ','
			}
			list += '?'
		}

		/*let chunks = await PlayerChunkDB.query("WITH region_real AS (SELECT chunk_x, chunk_z, world, uuid, ts, hash, chunk_x / 32.0 AS region_x_real, chunk_z / 32.0 AS region_z_real FROM player_chunk) " +
				"SELECT chunk_x, chunk_z, world, uuid, ts, hash AS data FROM region_real WHERE (cast (region_x_real as int) - (region_x_real < cast (region_x_real as int))) || \"_\" || (cast (region_z_real as int) - (region_z_real < cast (region_z_real as int))) IN (?) " +
				"AND world = ? ORDER BY ts DESC",
				[regionsAsString.join(","), world]);*/
		let chunks = await PlayerChunkDB.query(
			`WITH region_real AS (SELECT
				chunk_x, chunk_z, world, uuid, ts, hash,
				chunk_x / 32.0 AS region_x_real,
				chunk_z / 32.0 AS region_z_real
				FROM player_chunk
			) SELECT
				(
					cast (region_x_real as int) - (region_x_real < cast (region_x_real as int))
				) || "_" || (
					cast (region_z_real as int) - (region_z_real < cast (region_z_real as int))
				) AS region,
				chunk_x, chunk_z, world, uuid, ts,
				hash AS data
			FROM region_real
			WHERE region IN (${list}) AND world = ?
			ORDER BY ts DESC`,
			[...regionsAsString, world],
		)
		/*let chunks = await PlayerChunkDB.createQueryBuilder()
			.where('(chunk_x/32) || "_" || (chunk_z/32) IN (:...regions)', { regions: regionsAsString })
			.andWhere("world = :world", { world: world })
			.orderBy('ts', 'DESC')
			.getMany()*/

		const seenChunks: Record<string, PlayerChunkDB> = {}
		for (const chunk of chunks) {
			const chunkPos = `${chunk.chunk_x},${chunk.chunk_z}`
			if (seenChunks[chunkPos]) continue
			seenChunks[chunkPos] = chunk
		}
		return Object.values(seenChunks)
	}

	/** latest chunk at that location */
	static async getChunkWithData(chunk: {
		world: string
		chunk_x: number
		chunk_z: number
	}) {
		return await PlayerChunkDB.findOne({
			where: {
				world: chunk.world,
				chunk_x: chunk.chunk_x,
				chunk_z: chunk.chunk_z,
			},
			relations: ['data'], // include chunk data stored in other table
			order: { ts: 'DESC' }, // get latest among all players that sent this chunk
		})
	}
}

registerEntity(PlayerChunkDB)
