import { connectDB } from './db'
import { PlayerChunk, PlayerChunkDB } from './MapChunk'
import { ClientPacket, ProtocolClient, ProtocolHandler } from './protocol'
import { ChunkTilePacket } from './protocol/ChunkTilePacket'
import { TcpServer } from './server'

connectDB().then(() => new Main())

class Main implements ProtocolHandler {
	server = new TcpServer(this)

	handleClientConnected(client: ProtocolClient) {}

	handleClientAuthenticated(client: ProtocolClient) {
		// TODO check version, mc server, user access
	}

	handleClientDisconnected(client: ProtocolClient) {}

	handleClientPacketReceived(client: ProtocolClient, pkt: ClientPacket) {
		switch (pkt.type) {
			case 'ChunkTile':
				return this.handleChunkTilePacket(client, pkt)
			default:
				throw new Error(
					`Unknown packet '${(pkt as any).type}' from client ${client.id}`,
				)
		}
	}

	async handleChunkTilePacket(client: ProtocolClient, pkt: ChunkTilePacket) {
		if (!client.uuid) throw new Error(`Client${client.id} is not authenticated`)

		// TODO ignore if same chunk hash exists in db

		const playerChunk: PlayerChunk = {
			uuid: client.uuid,
			world: pkt.world,
			chunk_x: pkt.chunk_x,
			chunk_z: pkt.chunk_z,
			data: pkt.data,
			ts: Date.now(),
		}
		await PlayerChunkDB.store(playerChunk)

		// TODO small timeout, then skip if other client already has it
		for (const otherClient of Object.values(this.server.clients)) {
			if (client === otherClient) continue
			otherClient.send(pkt)
		}

		// TODO queue render
	}
}
