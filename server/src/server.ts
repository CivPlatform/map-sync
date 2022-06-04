import crypto from 'crypto'
import net from 'net'
import fetch from 'node-fetch'
import { Main } from './main'
import type { ClientPacket, ServerPacket } from './protocol'
import { decodePacket, encodePacket } from './protocol'
import { BufReader } from './protocol/BufReader'
import { BufWriter } from './protocol/BufWriter'
import { EncryptionResponsePacket } from './protocol/EncryptionResponsePacket'
import { HandshakePacket } from './protocol/HandshakePacket'

const { PORT = '12312', HOST = '127.0.0.1' } = process.env

type ProtocolHandler = Main // TODO cleanup

export class TcpServer {
	server: net.Server
	clients: Record<number, TcpClient> = {}

	keyPair = crypto.generateKeyPairSync('rsa', { modulusLength: 1024 })
	// precomputed for networking
	publicKeyBuffer = this.keyPair.publicKey.export({
		type: 'spki',
		format: 'der',
	})

	constructor(readonly handler: ProtocolHandler) {
		this.server = net.createServer({}, (socket) => {
			const client = new TcpClient(socket, this, handler)
			this.clients[client.id] = client
			socket.on('end', () => delete this.clients[client.id])
			socket.on('close', () => delete this.clients[client.id])
		})

		this.server.on('error', (err: Error) => {
			console.error('[TcpServer] Error:', err)
			this.server.close()
		})

		this.server.listen({ port: PORT, hostname: HOST }, () => {
			console.log('[TcpServer] Listening on', HOST, PORT)
		})
	}

	decrypt(buf: Buffer) {
		return crypto.privateDecrypt(
			{
				key: this.keyPair.privateKey,
				padding: crypto.constants.RSA_PKCS1_PADDING,
			},
			buf,
		)
	}
}

let nextClientId = 1

/** Prefixes packets with their length (UInt32BE);
 * handles Mojang authentication */
export class TcpClient {
	readonly id = nextClientId++
	/** contains mojang name once logged in */
	name = 'Client' + this.id

	modVersion: string | undefined
	gameAddress: string | undefined
	uuid: string | undefined
	mcName: string | undefined
	world: string | undefined

	whitelisted: boolean | undefined

	/** prevent Out of Memory when client sends a large packet */
	maxFrameSize = 2 ** 24

	/** sent by client during handshake */
	private claimedMojangName?: string
	private verifyToken?: Buffer
	/** we need to wait for the mojang auth response
	 * before we can en/decrypt packets following the handshake */
	private cryptoPromise?: Promise<{
		cipher: crypto.Cipher
		decipher: crypto.Decipher
	}>

	constructor(
		private socket: net.Socket,
		private server: TcpServer,
		private handler: ProtocolHandler,
	) {
		this.log('Connected from', socket.remoteAddress)
		handler.handleClientConnected(this)

		/** Accumulates received data, containing none, one, or multiple frames; the last frame may be partial only. */
		let accBuf: Buffer = Buffer.alloc(0)

		socket.on('data', async (data: Buffer) => {
			try {
				if (this.cryptoPromise) {
					const { decipher } = await this.cryptoPromise
					data = decipher.update(data)
				}

				// creating a new buffer every time is fine in our case, because we expect most frames to be large
				accBuf = Buffer.concat([accBuf, data])

				// we may receive multiple frames in one call
				while (true) {
					if (accBuf.length <= 4) return // wait for more data
					const frameSize = accBuf.readUInt32BE()

					// prevent Out of Memory
					if (frameSize > this.maxFrameSize) {
						return this.kick(
							'Frame too large: ' + frameSize + ' have ' + accBuf.length,
						)
					}

					if (accBuf.length < 4 + frameSize) return // wait for more data

					const frameReader = new BufReader(accBuf)
					frameReader.readUInt32() // skip frame size
					let pktBuf = frameReader.readBufLen(frameSize)
					accBuf = frameReader.readRemainder()

					const reader = new BufReader(pktBuf)

					try {
						const packet = decodePacket(reader)
						await this.handlePacketReceived(packet)
					} catch (err) {
						this.warn(err)
						return this.kick('Error in packet handler')
					}
				}
			} catch (err) {
				this.warn(err)
				return this.kick('Error in data handler')
			}
		})

		socket.on('close', (hadError: boolean) => {
			this.log('Closed.', { hadError })
		})

		socket.on('end', () => {
			this.log('Ended')
		})

		socket.on('timeout', () => {
			this.warn('Timeout')
		})

		socket.on('error', (err: Error) => {
			this.warn('Error:', err)
			this.kick('Socket error')
		})
	}

	private async handlePacketReceived(pkt: ClientPacket) {
		if (!this.uuid) {
			// not authenticated yet
			switch (pkt.type) {
				case 'Handshake':
					return await this.handleHandshakePacket(pkt)
				case 'EncryptionResponse':
					return await this.handleEncryptionResponsePacket(pkt)
			}
			throw new Error(`Packet ${pkt.type} from unauth'd client ${this.id}`)
		} else {
			return await this.handler.handleClientPacketReceived(this, pkt)
		}
	}

	kick(internalReason: string) {
		this.log(`Kicking:`, internalReason)
		this.socket.destroy()
	}

	async send(pkt: ServerPacket) {
		if (!this.cryptoPromise) {
			this.debug('Not encrypted, dropping packet', pkt.type)
			return
		}
		if (!this.uuid) {
			this.debug('Not authenticated, dropping packet', pkt.type)
			return
		}
		if (pkt.type !== 'Catchup') this.debug(this.mcName + " -> " + pkt.type);
		await this.sendInternal(pkt, true)
	}

	private async sendInternal(pkt: ServerPacket, doCrypto = false) {
		if (!this.socket.writable)
			return this.debug('Socket closed, dropping', pkt.type)
		if (doCrypto && !this.cryptoPromise)
			throw new Error(`Can't encrypt: handshake not finished`)

		const writer = new BufWriter() // TODO size hint
		writer.writeUInt32(0) // set later, but reserve space in buffer
		encodePacket(pkt, writer)
		let buf = writer.getBuffer()
		buf.writeUInt32BE(buf.length - 4, 0) // write into space reserved above

		if (doCrypto) {
			const { cipher } = await this.cryptoPromise!
			buf = cipher!.update(buf)
		}

		this.socket.write(buf)
	}

	private async handleHandshakePacket(packet: HandshakePacket) {
		if (this.cryptoPromise) throw new Error(`Already authenticated`)
		if (this.verifyToken) throw new Error(`Encryption already started`)

		this.modVersion = packet.modVersion
		this.gameAddress = packet.gameAddress
		this.claimedMojangName = packet.mojangName
		this.world = packet.world
		this.verifyToken = crypto.randomBytes(4)

		await this.sendInternal({
			type: 'EncryptionRequest',
			publicKey: this.server.publicKeyBuffer,
			verifyToken: this.verifyToken,
		})
	}

	private async handleEncryptionResponsePacket(pkt: EncryptionResponsePacket) {
		if (this.cryptoPromise) throw new Error(`Already authenticated`)
		if (!this.claimedMojangName)
			throw new Error(`Encryption has not started: no mojangName`)
		if (!this.verifyToken)
			throw new Error(`Encryption has not started: no verifyToken`)

		const verifyToken = this.server.decrypt(pkt.verifyToken)
		if (!this.verifyToken.equals(verifyToken)) {
			throw new Error(
				`verifyToken mismatch: got ${verifyToken} expected ${this.verifyToken}`,
			)
		}

		const secret = this.server.decrypt(pkt.sharedSecret)

		const shaHex = crypto
			.createHash('sha1')
			.update(secret)
			.update(this.server.publicKeyBuffer)
			.digest()
			.toString('hex')

		this.cryptoPromise = fetchHasJoined({
			username: this.claimedMojangName,
			shaHex,
		}).then(async (mojangAuth) => {
			if (!mojangAuth?.uuid) {
				this.kick(`Mojang auth failed`)
				throw new Error(`Mojang auth failed`)
			}

			this.log('Authenticated as', mojangAuth)

			this.uuid = mojangAuth.uuid
			this.mcName = mojangAuth.name
			this.name += ':' + mojangAuth.name

			return {
				cipher: crypto.createCipheriv('aes-128-cfb8', secret, secret),
				decipher: crypto.createDecipheriv('aes-128-cfb8', secret, secret),
			}
		})

		await this.cryptoPromise.then(async () => {
			await this.handler.handleClientAuthenticated(this)
		})
	}

	debug(...args: any[]) {
		if (process.env.NODE_ENV === 'production') return
		console.debug(`[${this.name}]`, ...args)
	}

	log(...args: any[]) {
		console.log(`[${this.name}]`, ...args)
	}

	warn(...args: any[]) {
		console.error(`[${this.name}]`, ...args)
	}
}

async function fetchHasJoined(args: {
	username: string
	shaHex: string
	clientIp?: string
}) {
	const { username, shaHex, clientIp } = args
	let url = `https://sessionserver.mojang.com/session/minecraft/hasJoined?username=${username}&serverId=${shaHex}`
	if (clientIp) url += `&ip=${clientIp}`
	const res = await fetch(url)
	try {
		if (res.status === 204) return null
		let { id, name } = (await res.json()) as { id: string; name: string }
		const uuid = id.replace(
			/^(........)-?(....)-?(....)-?(....)-?(............)$/,
			'$1-$2-$3-$4-$5',
		)
		return { uuid, name }
	} catch (err) {
		console.error(res)
		throw err
	}
}
