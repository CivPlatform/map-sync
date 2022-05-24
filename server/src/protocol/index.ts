import { BufReader } from './BufReader'
import { BufWriter } from './BufWriter'
import { ChunkTilePacket } from './ChunkTilePacket'
import { EncryptionRequestPacket } from './EncryptionRequestPacket'
import { EncryptionResponsePacket } from './EncryptionResponsePacket'
import { HandshakePacket } from './HandshakePacket'

export interface ProtocolClient {
	/** unique among all clients */
	readonly id: number
	/** human-friendly, for logging */
	readonly name: string

	readonly modVersion: string | undefined
	readonly gameAddress: string | undefined
	/** if set, client has authenticated with Mojang */
	readonly uuid: string | undefined

	send(packet: ServerPacket): void

	kick(internalReason: string): void
}

export interface ProtocolHandler {
	handleClientConnected(client: ProtocolClient): void
	handleClientAuthenticated(client: ProtocolClient): void
	handleClientDisconnected(client: ProtocolClient): void
	handleClientPacketReceived(client: ProtocolClient, packet: ClientPacket): void
}

export type ClientPacket =
	| ChunkTilePacket
	| EncryptionResponsePacket
	| HandshakePacket

export type ServerPacket = ChunkTilePacket | EncryptionRequestPacket

export const packetIds = [
	'ERROR:pkt0',
	'Handshake',
	'EncryptionRequest',
	'EncryptionResponse',
	'ChunkTile',
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
		default:
			throw new Error(`Unknown packet type ${packetType}`)
	}
}

export function encodePacket(pkt: ServerPacket, writer: BufWriter): void {
	writer.writeUInt8(getPacketId(pkt.type))
	switch (pkt.type) {
		case 'ChunkTile':
			return ChunkTilePacket.encode(pkt, writer)
		case 'EncryptionRequest':
			return EncryptionRequestPacket.encode(pkt, writer)
		default:
			throw new Error(`Unknown packet type ${(pkt as any).type}`)
	}
}
