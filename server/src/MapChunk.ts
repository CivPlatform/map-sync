import { Prisma } from '@prisma/client';
import {
	BaseEntity,
	Column,
	Entity,
	JoinColumn,
	ManyToOne,
	PrimaryColumn,
} from 'typeorm';
import { registerEntity } from './db';
import { prisma } from './prisma';

export interface PlayerChunk {
	world: string;
	chunk_x: number;
	chunk_z: number;
	uuid: string;
	ts: number;
	data: ChunkData;
}

export interface ChunkData {
	hash: Buffer;
	version: number;
	data: Buffer;
}

/** share same data of a chunk between multiple PlayerChunk (different players) */
@Entity({ name: 'chunk_data' })
export class ChunkDataDB extends BaseEntity implements ChunkData {
	static primaryCols = ['hash'];

	@PrimaryColumn({ type: 'blob' })
	hash!: Buffer;

	@Column({ type: 'integer' })
	version!: number;

	/** binary representation of map data */
	@Column({ type: 'blob' })
	data!: Buffer;
}

registerEntity(ChunkDataDB);

/** A chunk as the player saw it at that time.
 * DB stores the one most recent entry per player. */
@Entity({ name: 'player_chunk' })
export class PlayerChunkDB extends BaseEntity implements PlayerChunk {
	static primaryCols = ['world', 'chunk_x', 'chunk_z', 'uuid'];

	@PrimaryColumn({ type: 'text' })
	world!: string;

	@PrimaryColumn({ type: 'integer' })
	chunk_x!: number;

	@PrimaryColumn({ type: 'integer' })
	chunk_z!: number;

	/** player who sent this chunk */
	@PrimaryColumn({ type: 'text' })
	uuid!: string;

	@Column({ type: 'bigint' })
	ts!: number;

	@ManyToOne(() => ChunkDataDB)
	@JoinColumn({ name: 'hash' })
	data!: ChunkData;

	/**
	 * Saves a chunk
	 * @param map_chunk chunk
	 */
	static async store(map_chunk: PlayerChunk) {
		// TODO if PlayerChunk exists, and holds last reference to old hash, delete ChunkData at old hash

		// create object to store all data needed for chunk data
		// minue timestamp as we only want it added the first time a chunk is seen
		const chunkData = {
			data: map_chunk.data.data,
			hash: map_chunk.data.hash,
			version: map_chunk.data.version,
			chunkX: map_chunk.chunk_x,
			chunkZ: map_chunk.chunk_z,
			world: map_chunk.world,
		};

		// object for all player data minus timestamp because where can't take it
		// added later when actually saving
		const playerData = {
			world: map_chunk.world,
			chunkX: map_chunk.chunk_x,
			chunkZ: map_chunk.chunk_z,
			uuid: map_chunk.uuid,
		};

		prisma.playerChunk.upsert({
			where: {
				world_chunkX_chunkZ_uuid: playerData,
			},
			create: {
				...playerData,
				timestamp: map_chunk.ts,
				chunkData: {
					connectOrCreate: {
						where: {
							hash: map_chunk.data.hash,
						},
						create: { ...chunkData, timestamp: map_chunk.ts },
					},
				},
			},
			update: {
				...playerData,
				timestamp: map_chunk.ts,
				chunkData: {
					upsert: {
						create: { ...chunkData, timestamp: map_chunk.ts },
						update: chunkData,
					},
				},
			},
		});
	}

	static async getRegionTimestamps() {
		// computing region coordinates in SQL requires truncating, not rounding
		return await prisma.$queryRaw<any[]>(
			Prisma.sql`
			WITH region_real AS (SELECT
				chunkX / 32.0 AS region_x_real,
				chunkZ / 32.0 AS region_z_real,
				timestamp
				FROM PlayerChunk
			) SELECT
				cast (region_x_real as int) - (region_x_real < cast (region_x_real as int)) AS region_x,
				cast (region_z_real as int) - (region_z_real < cast (region_z_real as int)) AS region_z,
				MAX(timestamp) AS timestamp
			FROM region_real
			GROUP BY region_x, region_z
			ORDER BY region_x DESC`
		);

		// Should convert above to prisma query at some point
		/*
		return await prisma.playerChunk.groupBy({
			orderBy: {
				chunkX: 'desc',
			},
			by: ['chunkX', 'chunkZ'],
			_max: {
				timestamp: true,
			},
		})
		*/
	}

	static async getCatchupData(world: string, regions: number[]) {
		// TODO use TypeORM's API instead of building our own query string
		let regionsAsString: string[] = [];
		let list: string = '';
		for (let i = 0; i < regions.length; i += 2) {
			regionsAsString.push('' + regions[i] + '_' + regions[i + 1] + '');
			if (i > 0) {
				list += ',';
			}
			list += '?';
		}

		// const chunks = prisma.$queryRaw<
		// 	any[]
		// >(Prisma.sql`WITH region_real AS (SELECT
		// 	chunkX, chunkZ, world, uuid, timestamp, hash,
		// 	chunkX / 32.0 AS region_x_real,
		// 	chunkZ / 32.0 AS region_z_real
		// 	FROM player_chunk
		// ) SELECT
		// 	(
		// 		cast (region_x_real as int) - (region_x_real < cast (region_x_real as int))
		// 	) || "_" || (
		// 		cast (region_z_real as int) - (region_z_real < cast (region_z_real as int))
		// 	) AS region,
		// 	chunkX, chunkZ, world, uuid, timestamp,
		// 	hash AS data
		// FROM region_real
		// WHERE region IN (${list}) AND world = ?
		// ORDER BY ts DESC`);

		console.log({ regionsAsString, world, list });

		let chunks = await PlayerChunkDB.query(
			`WITH region_real AS (SELECT
				chunkX, chunkZ, world, uuid, timestamp, hash,
				chunkX / 32.0 AS region_x_real,
				chunkZ / 32.0 AS region_z_real
				FROM PlayerChunk
			) SELECT
				(
					cast (region_x_real as int) - (region_x_real < cast (region_x_real as int))
				) || "_" || (
					cast (region_z_real as int) - (region_z_real < cast (region_z_real as int))
				) AS region,
				chunkX, chunkZ, world, uuid, timestamp,
				hash AS data
			FROM region_real
			WHERE region IN (${list}) AND world = ?
			ORDER BY ts DESC`,
			[...regionsAsString, world]
		);

		const seenChunks: Record<string, PlayerChunkDB> = {};
		for (const chunk of chunks) {
			const chunkPos = `${chunk.chunk_x},${chunk.chunk_z}`;
			if (seenChunks[chunkPos]) continue;
			seenChunks[chunkPos] = chunk;
		}
		return Object.values(seenChunks);
	}

	/**
	 * Gets latest chunk at that location
	 * @param chunk
	 * @returns latest chunk
	 */
	static async getChunkWithData(chunk: {
		world: string;
		chunk_x: number;
		chunk_z: number;
	}) {
		return await prisma.playerChunk.findFirst({
			where: {
				world: chunk.world,
				chunkX: chunk.chunk_x,
				chunkZ: chunk.chunk_z,
			},
			include: {
				// include all chunk data
				chunkData: true,
			},
			orderBy: {
				// get latest chunk among all players
				timestamp: 'desc',
			},
		});
	}
}

registerEntity(PlayerChunkDB);
