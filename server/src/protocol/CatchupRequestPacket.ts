import { type CatchupChunk } from '../model'
import { BufReader } from './BufReader'

export interface CatchupRequestPacket {
	type: 'CatchupRequest'
	world: string
	chunks: CatchupChunk[]
}

export namespace CatchupRequestPacket {
	export function decode(reader: BufReader): CatchupRequestPacket {
		const world = reader.readString()
		const chunks: CatchupChunk[] = new Array(reader.readUInt32())
		for (let i = 0; i < chunks.length; i++) {
			chunks[i] = {
				chunkX: reader.readInt32(),
				chunkZ: reader.readInt32(),
				timestamp: reader.readUInt64(),
			}
		}
		return { type: 'CatchupRequest', world, chunks }
	}
}
