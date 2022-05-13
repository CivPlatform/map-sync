import 'reflect-metadata'
import { BaseEntity, Connection, createConnection } from 'typeorm'

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
			database: 'db.sqlite',
		})
	}
	return _dbp
}

export async function closeDB() {
	if (_dbp) await _dbp.then((db) => db.close())
}
