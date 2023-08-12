import { type CatchupChunk } from '../model'
import { BufWriter } from './BufWriter'

export interface CatchupPacket {
	type: 'Catchup'
	chunks: CatchupChunk[]
}

export namespace CatchupPacket {
	export function encode(pkt: CatchupPacket, writer: BufWriter) {
		if (!pkt.chunks[0]) throw new Error(`Catchup chunks must not be empty`)
		writer.writeString(pkt.chunks[0].world)
		writer.writeUInt32(pkt.chunks.length)
		for (const row of pkt.chunks) {
			writer.writeInt32(row.chunk_x)
			writer.writeInt32(row.chunk_z)
			writer.writeUInt64(row.ts)
		}
	}
}
