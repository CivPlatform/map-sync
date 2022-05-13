import { connectDB } from './db'
import { PlayerChunk, PlayerChunkDB } from './MapChunk'

async function main() {
	await connectDB()

	// XXX

	const playerChunk: PlayerChunk = {
		world: 'minecraft:overworld',
		chunk_x: 1,
		chunk_z: -2,
		uuid: 'abcd1234',
		ts: Date.now(),
		data: { hash: 'the hash', version: 1, data: Buffer.from('the data') },
	}

	await PlayerChunkDB.store(playerChunk)

	const chunks = await PlayerChunkDB.find({
		// where: { chunk_x: 0 },
		relations: { data: true },
	})

	console.log(chunks)
}

main()
