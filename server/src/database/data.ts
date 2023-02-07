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
    return database.get()
        .with("regions", (db) => db
            .selectFrom("player_chunk")
            .select([
                "world",
                (eb) => kysely.sql<string>`(cast(floor(${eb.ref("chunk_x")} / 32.0) as int) || '_' || cast(floor(${eb.ref("chunk_z")} / 32.0) as int))`.as("region"),
                "chunk_x as x",
                "chunk_z as z",
                (eb) => eb.fn.max("ts").as("timestamp")
            ])
            .groupBy(["world", "x", "z"])
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
