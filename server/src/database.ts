import { Database as BunSqliteDatabase } from "bun:sqlite";

import * as kysely from "kysely";
import { BunSqliteDialect } from "kysely-bun-sqlite";

import { DATA_FOLDER } from "./metadata.ts";
import { type Pos2D } from "./model.ts";

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
        region_x: kysely.Generated<number>;
        region_z: kysely.Generated<number>;
        uuid: string;
        ts: number;
        hash: Buffer;
    };
}

export function get() {
    return (database ??= new kysely.Kysely<Database>({
        dialect: new BunSqliteDialect({
            database: new BunSqliteDatabase(
                Bun.env["SQLITE_PATH"] ?? `${DATA_FOLDER}/db.sqlite`,
                {
                    create: true,
                    readwrite: true,
                },
            ),
        }),
    }));
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
        .addColumn("region_x", "integer", (col) =>
            col
                .generatedAlwaysAs(kysely.sql<number>`floor(chunk_x / 32.0)`)
                .notNull(),
        )
        .addColumn("region_z", "integer", (col) =>
            col
                .generatedAlwaysAs(kysely.sql<number>`floor(chunk_z / 32.0)`)
                .notNull(),
        )
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
export async function getRegionTimestamps(dimension: string) {
    return await get()
        .selectFrom("player_chunk")
        .select([
            "region_x as regionX",
            "region_z as regionZ",
            (eb) => eb.fn.max("ts").as("timestamp"),
        ])
        .where("world", "=", dimension)
        .groupBy(["regionX", "regionZ"])
        .orderBy("timestamp", "asc")
        .execute();
}

/**
 * Converts an array of region coords into an array of timestamped chunk coords.
 */
export async function getChunkTimestamps(dimension: string, regions: Pos2D[]) {
    return await get()
        .selectFrom("player_chunk")
        .select([
            "chunk_x as chunkX",
            "chunk_z as chunkZ",
            (eb) => eb.fn.max("ts").as("timestamp"),
        ])
        .where((eb) =>
            eb.or(
                regions.map((region) =>
                    eb.and([
                        eb("region_x", "=", region.x),
                        eb("region_z", "=", region.z),
                    ]),
                ),
            ),
        )
        .where("world", "=", dimension)
        .groupBy(["chunkX", "chunkZ"])
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
    return await get()
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
    return await get()
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
        .groupBy(["chunk_x", "chunk_z", "version", "data"])
        .orderBy("player_chunk.ts", "desc")
        .execute();
}
