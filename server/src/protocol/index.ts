import { BufReader } from './BufReader'
import { BufWriter } from './BufWriter'
import { ChunkTilePacket } from './ChunkTilePacket'
import { EncryptionRequestPacket } from './EncryptionRequestPacket'
import { EncryptionResponsePacket } from './EncryptionResponsePacket'
import { HandshakePacket } from './HandshakePacket'
import {CatchupPacket} from "./CatchupPacket";
import {CatchupRequestPacket} from "./CatchupRequestPacket";

export type ClientPacket =
	| ChunkTilePacket
	| EncryptionResponsePacket
	| HandshakePacket
	| CatchupRequestPacket

export type ServerPacket =
	| ChunkTilePacket
	| EncryptionRequestPacket
	| CatchupPacket

export const packetIds = [
	'ERROR:pkt0',
	'Handshake',
	'EncryptionRequest',
	'EncryptionResponse',
	'ChunkTile',
	'Catchup',
	'CatchupRequest'
]

export function getPacketId(type: ServerPacket['type']) {
	const id = packetIds.indexOf(type)
	if (id === -1) throw new Error(`Unknown packet type ${type}`)
	return id
}

export function decodePacket(reader: BufReader): ClientPacket {
	const packetType = reader.readUInt8()
	switch (packetIds[packetType]) {
		case 'ChunkTile':
			return ChunkTilePacket.decode(reader)
		case 'Handshake':
			return HandshakePacket.decode(reader)
		case 'EncryptionResponse':
			return EncryptionResponsePacket.decode(reader)
		case 'CatchupRequest':
			return CatchupRequestPacket.decode(reader)
		default:
			throw new Error(`Unknown packet type ${packetType}`)
	}
}

export function encodePacket(pkt: ServerPacket, writer: BufWriter): void {
	writer.writeUInt8(getPacketId(pkt.type))
	switch (pkt.type) {
		case 'ChunkTile':
			return ChunkTilePacket.encode(pkt, writer)
		case 'Catchup':
			return CatchupPacket.encode(pkt, writer)
		case 'EncryptionRequest':
			return EncryptionRequestPacket.encode(pkt, writer)
		default:
			throw new Error(`Unknown packet type ${(pkt as any).type}`)
	}
}
