import { spawn } from 'child_process'
import { Between } from 'typeorm'
import { promisify } from 'util'
import { PlayerChunkDB } from './MapChunk'

export async function renderTile(tileX: number, tileZ: number) {
	const allChunks = await PlayerChunkDB.find({
		where: {
			chunk_x: Between(tileX * 16 - 1, (tileX + 1) * 16 - 1),
			chunk_z: Between(tileZ * 16 - 1, (tileZ + 1) * 16 - 1),
		},
		order: { ts: 'DESC' }, // newest first
		relations: ['data'],
	})

	if (!allChunks.length) {
		console.error(`Skipping tile ${tileX},${tileZ} - no chunks`)
		return
	}

	// skip old chunks at same pos (from different players)
	const newestChunks = new Map<string, PlayerChunkDB>()
	for (const chunk of allChunks) {
		const chunkPos = `${chunk.chunk_x},${chunk.chunk_z}`
		if (newestChunks.has(chunkPos)) continue
		newestChunks.set(chunkPos, chunk)
	}
	const chunksList = Array.from(newestChunks.values())

	const proc = spawn(
		'../render/target/release/civmap-render',
		[String(tileX), String(tileZ), 'tiles'],
		{ cwd: '../render' }, // so render can find blocks.json
	)
	proc.stdout.pipe(process.stdout)
	proc.stderr.pipe(process.stderr)

	const write = promisify<Buffer, void>(proc.stdin.write.bind(proc.stdin))

	const numBuf = Buffer.alloc(4)
	numBuf.writeUInt32BE(chunksList.length)
	await write(numBuf)

	const chunkHeaderBuf = Buffer.alloc(4 + 4 + 2) // reused. 32+32+16 bit
	for (const chunk of chunksList) {
		chunkHeaderBuf.writeInt32BE(chunk.chunk_x, 0)
		chunkHeaderBuf.writeInt32BE(chunk.chunk_z, 4)
		chunkHeaderBuf.writeUInt16BE(chunk.data.version, 8)
		await write(chunkHeaderBuf)
		await write(chunk.data.data)
	}

	await new Promise((resolve) => proc.once('exit', resolve))
}
