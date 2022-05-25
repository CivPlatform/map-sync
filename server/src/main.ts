import { connectDB } from './db'
import { PlayerChunk, PlayerChunkDB } from './MapChunk'
import { ClientPacket, ProtocolClient, ProtocolHandler } from './protocol'
import { ChunkTilePacket } from './protocol/ChunkTilePacket'
import { TcpServer } from './server'
import {CatchupPacket} from "./protocol/CatchupPacket";

const MOD_VERSION = "1"

connectDB().then(() => new Main())

class Main implements ProtocolHandler {
	server = new TcpServer(this)

	handleClientConnected(client: ProtocolClient) {}

	async handleClientAuthenticated(client: ProtocolClient) {
		// TODO check version, mc server, user access

		// TODO for above: config file for version, mc server, user access

		// TODO send user the catchup tiles if possible
		let chunks = await PlayerChunkDB.find({
			order: {
				ts: "DESC"
			},
			take: 1260
		})

		while(chunks.length > 0){

		}
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
		if (!client.uuid) throw new Error(`${client.name} is not authenticated`)

		// TODO ignore if same chunk hash exists in db

		const playerChunk: PlayerChunk = {
			world: pkt.world,
			chunk_x: pkt.chunk_x,
			chunk_z: pkt.chunk_z,
			uuid: client.uuid,
			ts: pkt.ts,
			data: pkt.data,
		}
		PlayerChunkDB.store(playerChunk).catch(console.error)

		// TODO small timeout, then skip if other client already has it
		for (const otherClient of Object.values(this.server.clients)) {
			if (client === otherClient) continue
			otherClient.send(pkt)
		}

		// TODO queue render
	}

}
