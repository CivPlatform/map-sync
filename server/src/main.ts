import crypto from 'crypto'
import { connectDB } from './db'
import { PlayerChunk, PlayerChunkDB } from './MapChunk'
import { ClientPacket, ProtocolClient, ProtocolHandler } from './protocol'
import { ChunkTilePacket } from './protocol/ChunkTilePacket'
import { TcpServer } from './server'

connectDB().then(() => new Main())

class Main implements ProtocolHandler {
	server = new TcpServer(this)

	handleClientConnected(client: ProtocolClient) {
		// XXX challenge with mojang auth
		const serverId = crypto.randomBytes(4).toString('hex')
		client.verifyToken = crypto.randomBytes(4)
		const publicKeyStrArr = this.serverKey
			.exportKey('pkcs8-public-pem')
			.split('\n')
		let publicKeyStr = ''
		for (let i = 1; i < publicKeyStrArr.length - 1; i++) {
			publicKeyStr += publicKeyStrArr[i]
		}
		client.publicKey = Buffer.from(publicKeyStr, 'base64')

		client.sendUnencrypted({
			type: 'EncryptionRequest',
			serverId: serverId,
			publicKey: client.publicKeyBuffer,
			verifyToken: client.verifyToken,
		})
	}

	handleClientAuthenticated(client: ProtocolClient) {
		client.send({ type: 'Hello' })
	}

	handleClientDisconnected(client: ProtocolClient) {}

	handleClientPacketReceived(client: ProtocolClient, pkt: ClientPacket) {
		if (!client.uuid) {
			// not authenticated yet
			// switch (pkt.type) {
			// 	case 'Auth':
			// 		return XXX
			// }
			throw new Error(`Packet ${pkt.type} from unauth'd client ${client.id}`)
		}
		switch (pkt.type) {
			case 'ChunkTile':
				return this.handleChunkTilePacket(client, pkt)
			case 'Handshake':
				return this.handleHandshakePacket(client, pkt)
			case 'EncryptionResponse':
				return this.handleEncryptionResponsePacket(client, pkt)
			default:
				throw new Error(`Unknown client packet ${(pkt as any).type}`)
		}
	}

	async handleEncryptionResponsePacket(
		client: ProtocolClient,
		pkt: AuthResponsePacket,
	) {
		if (client.isEncrypted) throw new Error(`Already authenticated`)
		if (!client.verifyToken) throw new Error(`Encryption has not started`)

		const verifyToken = crypto.privateDecrypt(
			{
				key: this.serverKey.exportKey(),
				padding: crypto.constants.RSA_PKCS1_PADDING,
			},
			packet.verifyToken,
		)
		if (!client.verifyToken.equals(verifyToken)) {
			throw new Error('VerifyToken mismatch')
			return
		}

		const sharedSecret = crypto.privateDecrypt(
			{
				key: this.serverKey.exportKey(),
				padding: crypto.constants.RSA_PKCS1_PADDING,
			},
			packet.sharedSecret,
		)
		client.enableCrypto(sharedSecret)
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
