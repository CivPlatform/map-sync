import "reflect-metadata";
import { DataSource } from "typeorm";
import { ChunkDataDB, PlayerChunkDB } from "./entities";

import { DATA_FOLDER } from "../config/mod";
const SQLITE_PATH = process.env["SQLITE_PATH"] ?? `${DATA_FOLDER}/db.sqlite`;

let db: DataSource | null = null;

export async function connectDB() {
    if (!db) {
        db = await new DataSource({
            type: "sqlite",
            database: SQLITE_PATH,
            synchronize: true,
            entities: [
                ChunkDataDB,
                PlayerChunkDB
            ]
        }).initialize();
    }
    return db;
}

export async function closeDB() {
    if (db) db.destroy();
}
