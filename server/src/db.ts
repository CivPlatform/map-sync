import 'reflect-metadata'
import { BaseEntity, Connection, createConnection } from 'typeorm'

import { DATA_FOLDER, PATH_SEPARATOR } from "./metadata"

const SQLITE_PATH = process.env["SQLITE_PATH"] ?? `${DATA_FOLDER}${PATH_SEPARATOR}db.sqlite`

let _dbp: Promise<Connection> | null = null

// TODO accept any EntitySchema<any>[]
const entities: Record<string, typeof BaseEntity> = {}
export function registerEntity(entity: typeof BaseEntity) {
	entities[entity.name] = entity
	if (_dbp) _dbp.then((db) => db.close())
	_dbp = null // require reconnecting to apply entity
}

export function connectDB() {
	if (!_dbp) {
		_dbp = createConnection({
			entities: Object.values(entities),
			synchronize: true,
			type: 'sqlite',
			database: SQLITE_PATH,
		})
	}
	return _dbp
}

export async function closeDB() {
	if (_dbp) await _dbp.then((db) => db.close())
}
