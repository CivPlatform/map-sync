import { DatabaseSync } from "node:sqlite";
import { DATA_FOLDER } from "./metadata.ts";
import { type int16, type int32, type int64, type unt16 } from "./deps/ints.ts";
import type { CatchupChunk, CatchupRegion, StoredChunk } from "./model.ts";

let database: DatabaseSync | null = null;

export function get() {
    if (!database) {
        database = new DatabaseSync(
            process.env["SQLITE_PATH"] ?? `${DATA_FOLDER}/db.sqlite`,
            {
                readBigInts: true,
            },
        );
    }
    return database;
}

export function setup() {
    get().exec(`
        CREATE TABLE IF NOT EXISTS "chunk_data" (
            "hash" blob NOT NULL PRIMARY KEY,
            "version" INTEGER NOT NULL,
            "data" blob NOT NULL
        )
    `);
    get().exec(`
        CREATE TABLE IF NOT EXISTS "player_chunk" (
            "world" TEXT NOT NULL,
            "seed" INTEGER NOT NULL,
            "chunk_x" INTEGER NOT NULL,
            "chunk_z" INTEGER NOT NULL,
            "uuid" TEXT NOT NULL,
            "ts" BIGINT NOT NULL,
            "hash" blob NOT NULL,
            CONSTRAINT "PK_coords_and_player" PRIMARY KEY ("world", "chunk_x", "chunk_z", "uuid"),
            CONSTRAINT "FK_chunk_ref" FOREIGN KEY ("hash") REFERENCES "chunk_data" ("hash") ON DELETE NO ACTION ON UPDATE NO ACTION
        )
    `);
}

/**
 * Converts the entire database of player chunks into regions, with each region
 * having the highest (aka newest) timestamp.
 */
export function getRegionTimestamps(
    dimension: string,
    seed: int64,
): CatchupRegion[] {
    return get()
        .prepare(
            `
            SELECT
                FLOOR("chunk_x" / 32.0) AS "regionX",
                FLOOR("chunk_z" / 32.0) AS "regionZ",
                MAX("ts") AS "timestamp"
            FROM
                "player_chunk"
            WHERE
                "world" = ?
                "seed" = ?
            GROUP BY
                "regionX",
                "regionZ"
            ORDER BY
                "regionX" DESC
        `,
        )
        .all(dimension, seed) as unknown as CatchupRegion[];
}

/**
 * Converts an array of region coords into an array of timestamped chunk coords.
 */
export function getChunkTimestamps(
    dimension: string,
    seed: int64,
    regionX: int16,
    regionZ: int16,
): CatchupChunk[] {
    const minChunkX = regionX << 5n,
        maxChunkX = minChunkX + 32n;
    const minChunkZ = regionZ << 5n,
        maxChunkZ = minChunkZ + 32n;
    return get()
        .prepare(
            `
            SELECT
                "chunk_x" AS "chunkX",
                "chunk_z" AS "chunkZ",
                MAX("ts") AS "timestamp"
            FROM
                "player_chunk"
            WHERE
                "world" = ?
                "seed" = ?
                AND "chunk_x" >= ?
                AND "chunk_x" < ?
                AND "chunk_z" >= ?
                AND "chunk_z" < ?
            GROUP BY
                "chunk_x",
                "chunk_z"
            ORDER BY
                "ts" DESC
        `,
        )
        .all(
            dimension,
            seed,
            minChunkX,
            maxChunkX,
            minChunkZ,
            maxChunkZ,
        ) as unknown as CatchupChunk[];
}

/**
 * Retrieves the data for a given chunk's world, x, z, and timestamp.
 *
 * TODO: May want to consider making world, x, z, and timestamp a unique in the
 *       database table... may help performance.
 */
export function getChunkData(
    dimension: string,
    seed: int64,
    chunkX: int32,
    chunkZ: int32,
): StoredChunk | null {
    return (get()
        .prepare(
            `
            SELECT
                "chunk_data"."hash" AS "hash",
                "chunk_data"."version" AS "version",
                "chunk_data"."data" AS "data",
                "player_chunk"."ts" AS "timestamp"
            FROM
                "player_chunk"
                INNER JOIN "chunk_data" ON "chunk_data"."hash" = "player_chunk"."hash"
            WHERE
                "player_chunk"."world" = ?
                "player_chunk"."seed" = ?
                AND "player_chunk"."chunk_x" = ?
                AND "player_chunk"."chunk_z" = ?
            ORDER BY
                "player_chunk"."ts" DESC
            LIMIT 1
        `,
        )
        .get(dimension, seed, chunkX, chunkZ) ??
        null) as unknown as StoredChunk | null;
}

/**
 * Stores a player's chunk data.
 */
export function storeChunkData(
    dimension: string,
    seed: int64,
    chunkX: int32,
    chunkZ: int32,
    uuid: string,
    timestamp: int64,
    version: unt16,
    hash: Buffer,
    data: Buffer,
) {
    get()
        .prepare(
            `
        INSERT INTO
            "chunk_data" ("hash", "version", "data")
        VALUES
            (?, ?, ?)
        ON CONFLICT ("hash") DO NOTHING
    `,
        )
        .run(hash, version, data);
    get()
        .prepare(
            `
        REPLACE INTO "player_chunk" (
            "world",
            "seed",
            "chunk_x",
            "chunk_z",
            "uuid",
            "ts",
            "hash"
        )
        VALUES
            (?, ?, ?, ?, ?, ?)
    `,
        )
        .run(dimension, seed, chunkX, chunkZ, uuid, timestamp, hash);
}
