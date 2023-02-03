import "reflect-metadata";
import { BaseEntity, DataSource } from "typeorm";

import { DATA_FOLDER } from "../config/mod";
const SQLITE_PATH = process.env["SQLITE_PATH"] ?? `${DATA_FOLDER}/db.sqlite`;

let db: DataSource | null = null;

// TODO accept any EntitySchema<any>[]
const entities: Record<string, typeof BaseEntity> = {};
export function registerEntity(entity: typeof BaseEntity) {
    entities[entity.name] = entity;
    if (db) db.destroy();
    db = null; // require reconnecting to apply entity
}

export async function connectDB() {
    if (!db) {
        db = await new DataSource({
            type: "sqlite",
            database: SQLITE_PATH,
            synchronize: true,
            entities: entities
        }).initialize();
    }
    return db;
}

export async function closeDB() {
    if (db) db.destroy();
}
