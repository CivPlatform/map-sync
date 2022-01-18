import sqlite3 from 'sqlite3'

export interface MapChunk {
	world: string
	chunk_x: number
	chunk_z: number
	uuid: string
	ts: number
	hash: string
	/** binary representation of map data */
	data: Uint8Array
}

async function main() {
	const db = new SqliteDB('db.sqlite')
	db.all('', [])
}

main()

export class SqliteDB {
	private db: sqlite3.Database

	constructor(filename: string) {
		this.db = new sqlite3.Database(filename)
	}

	all<T = unknown>(sql: string, params: any[]): Promise<T[]> {
		return new Promise((res, rej) =>
			this.db.all(sql, params, (err, rows) => (err ? rej(err) : res(rows))),
		)
	}
}
