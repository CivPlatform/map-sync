import './cli';
import { connectDB } from './db';
import { PlayerChunk, PlayerChunkDB } from './MapChunk';
import { uuid_cache_store, getConfig, whitelist_check } from './metadata';
import { ClientPacket } from './protocol';
import { CatchupRequestPacket } from './protocol/CatchupRequestPacket';
import { ChunkTilePacket } from './protocol/ChunkTilePacket';
import { TcpClient, TcpServer } from './server';
import { RegionCatchupPacket } from './protocol/RegionCatchupPacket';

connectDB().then(() => new Main());

type ProtocolClient = TcpClient; // TODO cleanup

export class Main {
	server = new TcpServer(this);

	//Cannot be async, as it's caled from a synchronous constructor
	handleClientConnected(client: ProtocolClient) {}

	async handleClientAuthenticated(client: ProtocolClient) {
		if (!client.mcPlayerUuid) throw new Error('Client not authenticated');

		const { GAME_ADDRESS } = process.env;
		if (GAME_ADDRESS && client.mcServerAddress !== GAME_ADDRESS)
			throw new Error(
				`Client on wrong mc server ${client.mcServerAddress}`
			);

		await uuid_cache_store(client.mcPlayerName!, client.mcPlayerUuid);

		if ((await getConfig()).whitelist) {
			if (!whitelist_check(client.mcPlayerUuid)) {
				client.log(
					`Rejected unwhitelisted user ${client.mcPlayerName} (${client.mcPlayerUuid})`
				);
				client.kick(`Not whitelisted`);
				return;
			}
		}

		client.whitelisted = true;

		// TODO check version, mc server, user access

		const timestamps = await PlayerChunkDB.getRegionTimestamps();
		client.send({
			type: 'RegionTimestamps',
			world: client.mcServerWorld!,
			regions: timestamps,
		});
	}

	handleClientDisconnected(client: ProtocolClient) {}

	handleClientPacketReceived(client: ProtocolClient, pkt: ClientPacket) {
		if (!client.whitelisted) return;
		switch (pkt.type) {
			case 'ChunkTile':
				return this.handleChunkTilePacket(client, pkt);
			case 'CatchupRequest':
				return this.handleCatchupRequest(client, pkt);
			case 'RegionCatchup':
				return this.handleRegionCatchupPacket(client, pkt);
			default:
				throw new Error(
					`Unknown packet '${(pkt as any).type}' from client ${
						client.id
					}`
				);
		}
	}

	async handleChunkTilePacket(client: ProtocolClient, pkt: ChunkTilePacket) {
		if (!client.whitelisted) return;
		if (!client.mcPlayerUuid)
			throw new Error(`${client.name} is not authenticated`);

		// TODO ignore if same chunk hash exists in db

		const playerChunk: PlayerChunk = {
			world: pkt.world,
			chunk_x: pkt.chunk_x,
			chunk_z: pkt.chunk_z,
			uuid: client.mcPlayerUuid,
			ts: pkt.ts,
			data: pkt.data,
		};
		PlayerChunkDB.store(playerChunk).catch(console.error);

		// TODO small timeout, then skip if other client already has it
		for (const otherClient of Object.values(this.server.clients)) {
			if (client === otherClient) continue;
			otherClient.send(pkt);
		}

		// TODO queue tile render for web map
	}

	async handleCatchupRequest(
		client: ProtocolClient,
		pkt: CatchupRequestPacket
	) {
		client.debug(client.mcPlayerName + ' <- ' + pkt.type);
		if (!client.whitelisted) return;
		if (!client.mcPlayerUuid)
			throw new Error(`${client.name} is not authenticated`);

		for (const req of pkt.chunks) {
			const { world, chunk_x, chunk_z } = req;

			let chunk = await PlayerChunkDB.getChunkWithData({
				world,
				chunk_x,
				chunk_z,
			});
			if (!chunk || !chunk.chunkData) {
				console.error(
					`${client.name} requested unavailable chunk`,
					req
				);
				continue;
			}

			if (chunk.timestamp > req.ts) continue; // someone sent a new chunk, which presumably got relayed to the client
			if (chunk.timestamp < req.ts) continue; // the client already has a chunk newer than this

			client.send({
				type: 'ChunkTile',
				world: chunk.world,
				chunk_x: chunk.chunkX,
				chunk_z: chunk.chunkZ,
				ts: Number(chunk.timestamp),
				data: {
					version: chunk.chunkData.version,
					hash: chunk.chunkData.hash,
					data: chunk.chunkData.data,
				},
			});
		}
	}

	async handleRegionCatchupPacket(
		client: ProtocolClient,
		pkt: RegionCatchupPacket
	) {
		client.debug(client.mcPlayerName + ' <- ' + pkt.type);
		if (!client.whitelisted) return;
		if (!client.mcPlayerUuid)
			throw new Error(`${client.name} is not authenticated`);

		const chunks = await PlayerChunkDB.getCatchupData(
			pkt.world,
			pkt.regions
		);
		if (chunks.length) client.send({ type: 'Catchup', chunks });
	}
}
