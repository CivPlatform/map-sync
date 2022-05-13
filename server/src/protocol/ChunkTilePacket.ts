import { BufReader } from './BufReader'
import { BufWriter } from './BufWriter'

export interface ChunkTilePacket {
	type: 'ChunkTile'
	world: string
	chunk_x: number
	chunk_z: number
	data: { version: number; hash: string; data: Buffer }
}

export namespace ChunkTilePacket {
	export function decode(reader: BufReader): ChunkTilePacket {
		return {
			type: 'ChunkTile',
			world: reader.readString(),
			chunk_x: reader.readInt32(),
			chunk_z: reader.readInt32(),
			data: {
				version: reader.readUInt16(),
				hash: reader.readString(),
				data: reader.readRemainder(),
			},
		}
	}

	export function encode(pkt: ChunkTilePacket, writer: BufWriter) {
		writer.writeString(pkt.world)
		writer.writeInt32(pkt.chunk_x)
		writer.writeInt32(pkt.chunk_z)
		writer.writeUInt8(pkt.data.version)
		writer.writeString(pkt.data.hash)
		writer.writeBuf(pkt.data.data)
	}
}
