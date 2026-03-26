import * as kysely from "kysely";
import sqlite from "better-sqlite3";
import { DATA_FOLDER } from "./metadata";
import {
    asInt16,
    asInt32,
    asInt64,
    asUnt16,
    int16,
    int32,
    int64,
    unt16,
} from "./deps/ints";
import { CatchupChunk, CatchupRegion, StoredChunk } from "./model";

let database: kysely.Kysely<Database> | null = null;

export interface Database {
    chunk_data: {
        hash: Buffer;
        version: bigint;
        data: Buffer;
    };
    player_chunk: {
        world: string;
        chunk_x: bigint;
        chunk_z: bigint;
        uuid: string;
        ts: bigint;
        hash: Buffer;
    };
}

export function get() {
    if (!database) {
        database = new kysely.Kysely<Database>({
            dialect: new kysely.SqliteDialect({
                database: async () => {
                    const conn = sqlite(
                        process.env["SQLITE_PATH"] ??
                            `${DATA_FOLDER}/db.sqlite`,
                        {},
                    );
                    conn.defaultSafeIntegers(true);
                    return conn;
                },
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
export async function getRegionTimestamps(
    dimension: string,
): Promise<CatchupRegion[]> {
    // computing region coordinates in SQL requires truncating, not rounding
    return get()
        .selectFrom("player_chunk")
        .select([
            (eb) =>
                kysely.sql<bigint>`floor(${eb.ref("chunk_x")} / 32.0)`.as(
                    "regionX",
                ),
            (eb) =>
                kysely.sql<bigint>`floor(${eb.ref("chunk_z")} / 32.0)`.as(
                    "regionZ",
                ),
            (eb) => eb.fn.max("ts").as("timestamp"),
        ])
        .where("world", "=", dimension)
        .groupBy(["regionX", "regionZ"])
        .orderBy("regionX", "desc")
        .execute()
        .then(async (regions) =>
            regions.map((region) => ({
                regionX: asInt16(region.regionX),
                regionZ: asInt16(region.regionZ),
                timestamp: asInt64(region.timestamp),
            })),
        );
}

/**
 * Converts an array of region coords into an array of timestamped chunk coords.
 */
export async function getChunkTimestamps(
    dimension: string,
    regionX: int16,
    regionZ: int16,
): Promise<CatchupChunk[]> {
    const minChunkX = regionX << 5n,
        maxChunkX = minChunkX + 32n;
    const minChunkZ = regionZ << 5n,
        maxChunkZ = minChunkZ + 32n;
    return get()
        .selectFrom("player_chunk")
        .select([
            "chunk_x as chunkX",
            "chunk_z as chunkZ",
            (eb) => eb.fn.max("ts").as("timestamp"),
        ])
        .where("world", "=", dimension)
        .where("chunk_x", ">=", minChunkX)
        .where("chunk_x", "<", maxChunkX)
        .where("chunk_z", ">=", minChunkZ)
        .where("chunk_z", "<", maxChunkZ)
        .groupBy(["chunk_x", "chunk_z"])
        .orderBy("ts", "desc")
        .execute()
        .then(async (chunks) =>
            chunks.map((chunk) => ({
                chunkX: asInt32(chunk.chunkX),
                chunkZ: asInt32(chunk.chunkZ),
                timestamp: asInt64(chunk.timestamp),
            })),
        );
}

/**
 * Retrieves the data for a given chunk's world, x, z, and timestamp.
 *
 * TODO: May want to consider making world, x, z, and timestamp a unique in the
 *       database table... may help performance.
 */
export async function getChunkData(
    dimension: string,
    chunkX: int32,
    chunkZ: int32,
): Promise<StoredChunk | null> {
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
        .executeTakeFirst()
        .then(async (chunk) =>
            chunk
                ? {
                      hash: chunk.hash,
                      version: asUnt16(chunk.version),
                      timestamp: asInt64(chunk.ts),
                      data: chunk.data,
                  }
                : null,
        );
}

/**
 * Stores a player's chunk data.
 */
export async function storeChunkData(
    dimension: string,
    chunkX: int32,
    chunkZ: int32,
    uuid: string,
    timestamp: int64,
    version: unt16,
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
