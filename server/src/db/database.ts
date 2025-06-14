import { Database as BunSqliteDatabase } from "bun:sqlite";

import { Kysely, type Generated, Migrator } from "kysely";
import { BunSqliteDialect } from "kysely-bun-sqlite";

import { DATA_FOLDER } from "../metadata.ts";
import Migrations from "./migrations.ts";
import { type Pos2D } from "../model.ts";

let database: Kysely<Database> | null = null;

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
        gen_region_x: Generated<number>;
        gen_region_z: Generated<number>;
        gen_region_coord: Generated<string>;
        uuid: string;
        ts: number;
        hash: Buffer;
    };
}

export function get() {
    return (database ??= new Kysely<Database>({
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

export function getMigrations(): Migrator {
    return new Migrator({
        db: get(),
        provider: new Migrations(),
    });
}

/** Convenience function to migrate to latest */
export async function setup() {
    const results = await getMigrations().migrateToLatest();
    for (const result of results.results ?? []) {
        switch (result.status) {
            case "Success":
                console.info(`Migration [${result.migrationName}] applied!`);
                break;
            case "Error":
                console.error(
                    `Migration [${result.migrationName}] failed to apply!`,
                );
                break;
            case "NotExecuted":
                console.warn(
                    `Migration [${result.migrationName}] was not applied!`,
                );
                break;
        }
    }
    if (results.error) {
        throw results.error;
    }
}

/**
 * Gets the timestamps for ALL regions stored.
 */
export async function getRegionTimestamps(dimension: string) {
    return await get()
        .selectFrom("player_chunk")
        .select([
            "gen_region_x as regionX",
            "gen_region_z as regionZ",
            (eb) => eb.fn.max("ts").as("timestamp"),
        ])
        .where("world", "=", dimension)
        .groupBy(["regionX", "regionZ"])
        .orderBy("timestamp", "asc")
        .execute();
}

export async function getChunkTimestamps(dimension: string, regions: Pos2D[]) {
    return await get()
        .selectFrom("player_chunk")
        .select([
            "chunk_x as chunkX",
            "chunk_z as chunkZ",
            (eb) => eb.fn.max("ts").as("timestamp"),
        ])
        .where(
            "gen_region_coord",
            "in",
            regions.map((region) => region.x + "_" + region.z),
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
        .transaction()
        .execute(async (transaction) => {
            await transaction
                .insertInto("chunk_data")
                .values({ hash, version, data })
                .onConflict((oc) => oc.column("hash").doNothing())
                .execute();
            await transaction
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
        });
}

/**
 * Gets all the [latest] chunks within a region.
 */
export async function getRegionChunks(
    dimension: string,
    regionX: number,
    regionZ: number,
) {
    return await get()
        .selectFrom("player_chunk")
        .innerJoin("chunk_data", "chunk_data.hash", "player_chunk.hash")
        .select([
            "player_chunk.chunk_x as chunkX",
            "player_chunk.chunk_z as chunkZ",
            (eb) => eb.fn.max("player_chunk.ts").as("timestamp"),
            "chunk_data.version as version",
            "chunk_data.data as data",
        ])
        .where("player_chunk.world", "=", dimension)
        .where("player_chunk.gen_region_x", "=", regionX)
        .where("player_chunk.gen_region_z", "=", regionZ)
        .groupBy(["chunkX", "chunkZ", "version", "data"])
        .orderBy("timestamp", "desc")
        .execute();
}
