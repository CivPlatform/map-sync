import { BufReader } from './BufReader'
import { BufWriter } from './BufWriter'

export interface HandshakePacket {
	type: 'Handshake'
	modVersion: string
	mcName: string
	gameAddress: string
}

export namespace HandshakePacket {
	export function decode(reader: BufReader): HandshakePacket {
		return {
			type: 'Handshake',
			modVersion: reader.readString(),
			mcName: reader.readString(),
			gameAddress: reader.readString(),
		}
	}

	export function encode(pkt: HandshakePacket, writer: BufWriter) {
		writer.writeString(pkt.modVersion)
		writer.writeString(pkt.mcName)
		writer.writeString(pkt.gameAddress)
	}
}
