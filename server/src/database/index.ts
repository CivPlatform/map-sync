import * as kysely from "kysely";
import sqlite from "better-sqlite3";
import { DATA_FOLDER } from "../config/mod";

let db: kysely.Kysely<Database> | null = null;

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
        ts: bigint;
        hash: Buffer;
    };
}

export function get() {
    if (!db) {
        db = new kysely.Kysely<Database>({
            dialect: new kysely.SqliteDialect({
                database: async () => sqlite(process.env["SQLITE_PATH"] ?? `${DATA_FOLDER}/db.sqlite`, {})
            })
        });
    }
    return db;
}

export async function setup() {
    await get().schema
        .createTable("chunk_data")
        .ifNotExists()
        .addColumn("hash", "blob", (col) => col.notNull().primaryKey())
        .addColumn("version", "integer", (col) => col.notNull())
        .addColumn("data", "blob", (col) => col.notNull())
        .execute();
    await get().schema
        .createTable("player_chunk")
        .ifNotExists()
        .addColumn("world", "text", (col) => col.notNull())
        .addColumn("chunk_x", "integer", (col) => col.notNull())
        .addColumn("chunk_z", "integer", (col) => col.notNull())
        .addColumn("uuid", "text", (col) => col.notNull())
        .addColumn("ts", "bigint", (col) => col.notNull())
        // TODO: Consider making this non-null like the other columns
        .addColumn("hash", "blob")
        .addPrimaryKeyConstraint("PK_coords_and_player", ["world", "chunk_x", "chunk_z", "uuid"])
        .addForeignKeyConstraint("FK_chunk_ref", ["hash"], "chunk_data", ["hash"],
            (fk) => fk.onUpdate("no action").onDelete("no action"))
        .execute();
}
