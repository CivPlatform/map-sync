import './cli'
import { connectDB } from './db'
import { PlayerChunk, PlayerChunkDB } from './MapChunk'
import { uuid_cache_store, config, whitelist_check } from './metadata'
import { ClientPacket } from './protocol'
import { CatchupPacket } from './protocol/CatchupPacket'
import { CatchupRequestPacket } from './protocol/CatchupRequestPacket'
import { ChunkTilePacket } from './protocol/ChunkTilePacket'
import { TcpClient, TcpServer } from './server'

connectDB().then(() => new Main())

type ProtocolClient = TcpClient // TODO cleanup

export class Main {
	server = new TcpServer(this)

	async handleClientConnected(client: ProtocolClient) {}

	async handleClientAuthenticated(client: ProtocolClient) {
		if (!client.uuid) throw new Error('Client not authenticated')

		uuid_cache_store(client.mcName!, client.uuid)

		if (config.whitelist) {
			if (!whitelist_check(client.uuid)) {
				client.log(
					`Rejected unwhitelisted user ${client.mcName} (${client.uuid})`,
				)
				client.kick(`Not whitelisted`)
				throw new Error(`Not whitelisted`)
			}
		}

		// TODO check version, mc server, user access

		if (!client.lastTimestamp)
			throw new Error(`${client.name} has not set a lastTimestamp`)

		const chunks = await PlayerChunkDB.getCatchupData(client.lastTimestamp)
		if (chunks.length) client.send({ type: 'Catchup', chunks })
	}

	handleClientDisconnected(client: ProtocolClient) {}

	handleClientPacketReceived(client: ProtocolClient, pkt: ClientPacket) {
		switch (pkt.type) {
			case 'ChunkTile':
				return this.handleChunkTilePacket(client, pkt)
			case 'CatchupRequest':
				return this.handleCatchupRequest(client, pkt)
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

		// TODO queue tile render for web map
	}

	async handleCatchupRequest(
		client: ProtocolClient,
		pkt: CatchupRequestPacket,
	) {
		if (!client.uuid) throw new Error(`${client.name} is not authenticated`)

		for (const req of pkt.chunks) {
			const { world, chunk_x, chunk_z } = req

			let chunk = await PlayerChunkDB.getChunkWithData({
				world,
				chunk_x,
				chunk_z,
			})
			if (!chunk) {
				console.error(`${client.name} requested unavailable chunk`, req)
				continue
			}

			if (chunk.ts > req.ts) continue // someone sent a new chunk, which presumably got relayed to the client
			if (chunk.ts < req.ts) continue // the client already has a chunk newer than this

			client.send({ type: 'ChunkTile', ...chunk })
		}
	}
}
