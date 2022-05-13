import { BufReader } from './BufReader'
import { BufWriter } from './BufWriter'
import { ChunkTilePacket } from './ChunkTilePacket'

export interface ProtocolClient {
	/** unique among all clients */
	readonly id: number

	/** if set, client has authenticated with Mojang */
	uuid: string | undefined

	send(packet: ServerPacket): void
}

export interface ProtocolHandler {
	handleClientConnected(client: ProtocolClient): void
	handleClientDisconnected(client: ProtocolClient): void
	handleClientPacketReceived(client: ProtocolClient, packet: ClientPacket): void
}

export const packetIds = ['ERROR', 'Auth', 'ChunkTile', 'SyncTime']

export function getPacketId(type: ServerPacket['type']) {
	const id = packetIds.indexOf(type)
	if (id === -1) throw new Error(`Unknown packet type ${type}`)
	return id
}

export type ClientPacket = ChunkTilePacket

export type ServerPacket = ChunkTilePacket

export function decodePacket(pktBuf: Buffer): ClientPacket {
	const reader = new BufReader(pktBuf)
	const packetType = reader.readUInt8()
	switch (packetIds[packetType]) {
		case 'ChunkTile':
			return ChunkTilePacket.decode(reader)
		default:
			throw new Error(`Unknown packet type ${packetType}`)
	}
}

export function encodePacket(pkt: ServerPacket): Buffer {
	const writer = new BufWriter() // TODO size hint
	writer.writeUInt8(getPacketId(pkt.type))
	switch (pkt.type) {
		case 'ChunkTile':
			ChunkTilePacket.encode(pkt, writer)
			return writer.getBuffer()
		default:
			throw new Error(`Unknown packet type ${pkt.type}`)
	}
}
