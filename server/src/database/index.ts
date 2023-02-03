import "reflect-metadata";
import { BaseEntity, Connection, createConnection } from "typeorm";

import { DATA_FOLDER } from "../metadata";
const SQLITE_PATH = process.env["SQLITE_PATH"] ?? `${DATA_FOLDER}/db.sqlite`;

let db: Promise<Connection> | null = null;

// TODO accept any EntitySchema<any>[]
const entities: Record<string, typeof BaseEntity> = {};
export function registerEntity(entity: typeof BaseEntity) {
    entities[entity.name] = entity;
    if (db) db.then((db) => db.close());
    db = null; // require reconnecting to apply entity
}

export function connectDB() {
    if (!db) {
        db = createConnection({
            entities: Object.values(entities),
            synchronize: true,
            type: "sqlite",
            database: SQLITE_PATH
        });
    }
    return db;
}

export async function closeDB() {
    if (db) await db.then((db) => db.close());
}
