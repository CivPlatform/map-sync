import * as kysely from "kysely";
import sqlite from "better-sqlite3";
import { DATA_FOLDER } from "./metadata";
import { type Pos2D } from "./model";

let database: kysely.Kysely<Database> | null = null;

export interface Database {
    chunk_data: {
        hash: Buffer;
        version: number;
        data: Buffer;
    };
    player_chunk: {
        world: string;
        chunk_x: number;
        chunk_z: number;
        uuid: string;
        ts: number;
        hash: Buffer;
    };
}

export function get() {
    if (!database) {
        database = new kysely.Kysely<Database>({
            dialect: new kysely.SqliteDialect({
                database: async () =>
                    sqlite(
                        process.env["SQLITE_PATH"] ??
                            `${DATA_FOLDER}/db.sqlite`,
                        {},
                    ),
            }),
        });
    }
    return database;
}

export async function setup() {
    await get()
        .schema.createTable("chunk_data")
        .ifNotExists()
        .addColumn("hash", "blob", (col) => col.notNull().primaryKey())
        .addColumn("version", "integer", (col) => col.notNull())
        .addColumn("data", "blob", (col) => col.notNull())
        .execute();
    await get()
        .schema.createTable("player_chunk")
        .ifNotExists()
        .addColumn("world", "text", (col) => col.notNull())
        .addColumn("chunk_x", "integer", (col) => col.notNull())
        .addColumn("chunk_z", "integer", (col) => col.notNull())
        .addColumn("uuid", "text", (col) => col.notNull())
        .addColumn("ts", "bigint", (col) => col.notNull())
        .addColumn("hash", "blob", (col) => col.notNull())
        .addPrimaryKeyConstraint("PK_coords_and_player", [
            "world",
            "chunk_x",
            "chunk_z",
            "uuid",
        ])
        .addForeignKeyConstraint(
            "FK_chunk_ref",
            ["hash"],
            "chunk_data",
            ["hash"],
            (fk) => fk.onUpdate("no action").onDelete("no action"),
        )
        .execute();
}

/**
 * Converts the entire database of player chunks into regions, with each region
 * having the highest (aka newest) timestamp.
 */
export function getRegionTimestamps(dimension: string) {
    // computing region coordinates in SQL requires truncating, not rounding
    return get()
        .selectFrom("player_chunk")
        .select([
            (eb) =>
                kysely.sql<number>`floor(${eb.ref("chunk_x")} / 32.0)`.as(
                    "regionX",
                ),
            (eb) =>
                kysely.sql<number>`floor(${eb.ref("chunk_z")} / 32.0)`.as(
                    "regionZ",
                ),
            (eb) => eb.fn.max("ts").as("timestamp"),
        ])
        .where("world", "=", dimension)
        .groupBy(["regionX", "regionZ"])
        .orderBy("regionX", "desc")
        .execute();
}

/**
 * Converts an array of region coords into an array of timestamped chunk coords.
 */
export async function getChunkTimestamps(dimension: string, regions: Pos2D[]) {
    return get()
        .with("regions", (db) =>
            db
                .selectFrom("player_chunk")
                .select([
                    (eb) =>
                        kysely.sql<string>`(cast(floor(${eb.ref(
                            "chunk_x",
                        )} / 32.0) as int) || '_' || cast(floor(${eb.ref(
                            "chunk_z",
                        )} / 32.0) as int))`.as("region"),
                    "chunk_x as x",
                    "chunk_z as z",
                    (eb) => eb.fn.max("ts").as("timestamp"),
                ])
                .where("world", "=", dimension)
                .groupBy(["x", "z"]),
        )
        .selectFrom("regions")
        .select(["x as chunkX", "z as chunkZ", "timestamp"])
        .where(
            "region",
            "in",
            regions.map((region) => region.x + "_" + region.z),
        )
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
    dimension: string,
    chunkX: number,
    chunkZ: number,
) {
    return get()
        .selectFrom("player_chunk")
        .innerJoin("chunk_data", "chunk_data.hash", "player_chunk.hash")
        .select([
            "chunk_data.hash as hash",
            "chunk_data.version as version",
            "chunk_data.data as data",
            "player_chunk.ts as ts",
        ])
        .where("player_chunk.world", "=", dimension)
        .where("player_chunk.chunk_x", "=", chunkX)
        .where("player_chunk.chunk_z", "=", chunkZ)
        .orderBy("player_chunk.ts", "desc")
        .limit(1)
        .executeTakeFirst();
}

/**
 * Stores a player's chunk data.
 */
export async function storeChunkData(
    dimension: string,
    chunkX: number,
    chunkZ: number,
    uuid: string,
    timestamp: number,
    version: number,
    hash: Buffer,
    data: Buffer,
) {
    await get()
        .insertInto("chunk_data")
        .values({ hash, version, data })
        .onConflict((oc) => oc.column("hash").doNothing())
        .execute();
    await get()
        .replaceInto("player_chunk")
        .values({
            world: dimension,
            chunk_x: chunkX,
            chunk_z: chunkZ,
            uuid,
            ts: timestamp,
            hash,
        })
        .execute();
}

/**
 * Gets all the [latest] chunks within a region.
 */
export async function getRegionChunks(
    dimension: string,
    regionX: number,
    regionZ: number,
) {
    const minChunkX = regionX << 4,
        maxChunkX = minChunkX + 16;
    const minChunkZ = regionZ << 4,
        maxChunkZ = minChunkZ + 16;
    return get()
        .selectFrom("player_chunk")
        .innerJoin("chunk_data", "chunk_data.hash", "player_chunk.hash")
        .select([
            "player_chunk.chunk_x as chunk_x",
            "player_chunk.chunk_z as chunk_z",
            (eb) => eb.fn.max("player_chunk.ts").as("timestamp"),
            "chunk_data.version as version",
            "chunk_data.data as data",
        ])
        .where("player_chunk.world", "=", dimension)
        .where("player_chunk.chunk_x", ">=", minChunkX)
        .where("player_chunk.chunk_x", "<", maxChunkX)
        .where("player_chunk.chunk_z", ">=", minChunkZ)
        .where("player_chunk.chunk_z", "<", maxChunkZ)
        .orderBy("player_chunk.ts", "desc")
        .execute();
}
