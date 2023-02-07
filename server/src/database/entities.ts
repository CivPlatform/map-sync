import * as kysely from "kysely";
import * as database from "./index";
import { Pos2D } from "../protocol/structs";

/**
 * Converts the entire database of player chunks into regions, with each region
 * having the highest (aka newest) timestamp.
 */
export function getRegionTimestamps() {
    // computing region coordinates in SQL requires truncating, not rounding
    return database.get()
        .selectFrom("player_chunk")
        .select([
            (eb) => kysely.sql<number>`floor(${eb.ref("chunk_x")} / 32.0)`.as("x"),
            (eb) => kysely.sql<number>`floor(${eb.ref("chunk_z")} / 32.0)`.as("z"),
            (eb) => eb.fn.max("ts").as("timestamp")
        ])
        .groupBy(["x", "z"])
        .orderBy("x", "desc")
        .execute();
}

/**
 * Converts an array of region coords into an array of timestamped chunk coords.
 */
export async function getChunkTimestamps(world: string, regions: Pos2D[]) {
    const chunks = await database.get()
        .with("regions", (db) => db
            .selectFrom("player_chunk")
            .select([
                "world",
                (eb) => kysely.sql<string>`(cast(floor(${eb.ref("chunk_x")} / 32.0) as int) || '_' || cast(floor(${eb.ref("chunk_z")} / 32.0) as int))`.as("region"),
                "chunk_x as x",
                "chunk_z as z",
                "ts as timestamp",
            ])
        )
        .selectFrom("regions")
        .select([
            "x",
            "z",
            "timestamp"
        ])
        .where("world", "=", world)
        .where("region", "in", regions.map((region) => region.x + "_" + region.z))
        .orderBy("timestamp", "desc")
        .execute();
    const seenChunks = new Set<string>();
    return chunks.filter((chunk) => {
        const chunkKey = chunk.x + "," + chunk.z;
        if (seenChunks.has(chunkKey)) {
            return false;
        }
        seenChunks.add(chunkKey);
        return true;
    });
}

/**
 * Retrieves the data for a given chunk's world, x, z, and timestamp.
 *
 * TODO: May want to consider making world, x, z, and timestamp a unique in the
 *       database table... may help performance.
 */
export async function getChunkData(
    world: string,
    x: number,
    z: number,
    timestamp: bigint
) {
    return database.get()
        .selectFrom("player_chunk")
        .innerJoin("chunk_data", "chunk_data.hash", "player_chunk.hash")
        .select([
            "chunk_data.hash as hash",
            "chunk_data.version as version",
            "chunk_data.data as data"
        ])
        .where("player_chunk.world", "=", world)
        .where("player_chunk.chunk_x", "=", x)
        .where("player_chunk.chunk_z", "=", z)
        .where("player_chunk.ts", "=", timestamp)
        .orderBy("player_chunk.ts", "desc")
        .limit(1)
        .executeTakeFirst();
}

/**
 * Stores a player's chunk data.
 */
export async function storeChunkData(
    world: string,
    x: number,
    z: number,
    uuid: string,
    timestamp: bigint,
    hash: Buffer,
    version: number,
    data: Buffer
) {
    await database.get()
        .insertInto("chunk_data")
        .values({ hash, version, data })
        .onConflict((oc) => oc.column("hash").doNothing())
        .execute();
    await database.get()
        .replaceInto("player_chunk")
        .values({
            world,
            chunk_x: x,
            chunk_z: z,
            uuid,
            ts: timestamp,
            hash
        })
        .execute();
}

import {
    BaseEntity,
    Column,
    Entity,
    JoinColumn,
    ManyToOne,
    PrimaryColumn
} from "typeorm";

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
@Entity({ name: "chunk_data" })
export class ChunkDataDB extends BaseEntity implements ChunkData {
    static primaryCols = ["hash"];

    @PrimaryColumn({ type: "blob" })
    hash!: Buffer;

    @Column({ type: "integer" })
    version!: number;

    /** binary representation of map data */
    @Column({ type: "blob" })
    data!: Buffer;
}

/** A chunk as the player saw it at that time.
 * DB stores the one most recent entry per player. */
@Entity({ name: "player_chunk" })
export class PlayerChunkDB extends BaseEntity implements PlayerChunk {
    static primaryCols = ["world", "chunk_x", "chunk_z", "uuid"];

    @PrimaryColumn({ type: "text" })
    world!: string;

    @PrimaryColumn({ type: "integer" })
    chunk_x!: number;

    @PrimaryColumn({ type: "integer" })
    chunk_z!: number;

    /** player who sent this chunk */
    @PrimaryColumn({ type: "text" })
    uuid!: string;

    @Column({ type: "bigint" })
    ts!: number;

    @ManyToOne(() => ChunkDataDB)
    @JoinColumn({ name: "hash" })
    data!: ChunkData;

}
