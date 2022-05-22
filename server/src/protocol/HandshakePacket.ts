import { BufReader } from './BufReader'
import { BufWriter } from './BufWriter'

export interface HandshakePacket {
	type: 'Handshake'
	modVersion: string
	mojangName: string
	gameAddress: string
}

export namespace HandshakePacket {
	export function decode(reader: BufReader): HandshakePacket {
		return {
			type: 'Handshake',
			modVersion: reader.readString(),
			mojangName: reader.readString(),
			gameAddress: reader.readString(),
		}
	}

	export function encode(pkt: HandshakePacket, writer: BufWriter) {
		writer.writeString(pkt.modVersion)
		writer.writeString(pkt.mojangName)
		writer.writeString(pkt.gameAddress)
	}
}
