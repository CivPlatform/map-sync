import crypto from 'crypto'
import net from 'net'
import fetch from 'node-fetch'
import type {
	ClientPacket,
	ProtocolClient,
	ProtocolHandler,
	ServerPacket,
} from './protocol'
import { decodePacket, encodePacket } from './protocol'
import { BufReader } from './protocol/BufReader'
import { BufWriter } from './protocol/BufWriter'
import { EncryptionResponsePacket } from './protocol/EncryptionResponsePacket'
import { HandshakePacket } from './protocol/HandshakePacket'

const { PORT = '12312', HOST = '127.0.0.1' } = process.env

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

/** Prefixes packets with their length (UInt32BE) */
export class TcpClient implements ProtocolClient {
	readonly id = nextClientId++

	modVersion: string | undefined
	gameAddress: string | undefined
	uuid: string | undefined

	/** prevent Out of Memory when client sends a large packet */
	maxFrameSize = 2 ** 24

	/** sent by client during handshake */
	private claimedMojangName?: string
	private verifyToken?: Buffer
	private cipher?: crypto.Cipher
	private decipher?: crypto.Decipher

	private get isEncrypted() {
		return !!(this.cipher && this.decipher)
	}

	constructor(
		private socket: net.Socket,
		private server: TcpServer,
		private handler: ProtocolHandler,
	) {
		this.log('Connected from', socket.remoteAddress)
		handler.handleClientConnected(this)

		/** Accumulates received data, containing none, one, or multiple frames; the last frame may be partial only. */
		let accBuf: Buffer = Buffer.alloc(0)

		socket.on('data', (data: Buffer) => {
			try {
				this.debug('Received', data.length, 'bytes')
				// creating a new buffer every time is fine in our case, because we expect most frames to be large
				accBuf = Buffer.concat([accBuf, data])

				// we may receive multiple frames in one call
				while (true) {
					if (accBuf.length <= 4) return // wait for more data
					const frameSize = accBuf.readUInt32BE()

					// prevent Out of Memory
					if (frameSize > this.maxFrameSize)
						return this.kick('Frame too large: ' + frameSize)

					if (accBuf.length < 4 + frameSize) return // wait for more data

					const frameReader = new BufReader(accBuf)
					frameReader.readUInt32() // skip frame size
					let pktBuf = frameReader.readBufLen(frameSize)
					accBuf = frameReader.readRemainder()

					if (this.decipher) {
						pktBuf = this.decipher.update(pktBuf)
					}

					const reader = new BufReader(pktBuf)

					try {
						const packet = decodePacket(reader)
						this.handlePacketReceived(packet)
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
			this.cipher?.final()
			this.decipher?.final()
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

	private handlePacketReceived(pkt: ClientPacket) {
		if (!this.uuid) {
			// not authenticated yet
			switch (pkt.type) {
				case 'Handshake':
					return this.handleHandshakePacket(pkt)
				case 'EncryptionResponse':
					return this.handleEncryptionResponsePacket(pkt)
			}
			throw new Error(`Packet ${pkt.type} from unauth'd client ${this.id}`)
		} else {
			this.handler.handleClientPacketReceived(this, pkt)
		}
	}

	kick(internalReason: string) {
		this.log(`Kicking:`, internalReason)
		this.socket.end()
	}

	send(pkt: ServerPacket) {
		if (!this.cipher) {
			this.debug('Not encrypted, dropping packet', pkt.type)
			return
		}
		if (!this.socket.writable) return
		const writer = new BufWriter() // TODO size hint
		encodePacket(pkt, writer)
		const pktBufRaw = writer.getBuffer()
		const pktBuf = this.cipher.update(pktBufRaw)
		this.frameAndSend(pktBuf)
	}

	private sendUnencrypted(pkt: ServerPacket) {
		if (!this.socket.writable)
			return this.debug('Socket closed, dropping', pkt.type)
		const writer = new BufWriter() // TODO size hint
		encodePacket(pkt, writer)
		const pktBuf = writer.getBuffer()
		this.frameAndSend(pktBuf)
	}

	private frameAndSend(pktBuf: Buffer) {
		const lenBuf = Buffer.alloc(4)
		lenBuf.writeUInt32BE(pktBuf.length)
		if (!this.socket.writable)
			return this.debug('Socket closed, dropping', pktBuf.length, 'bytes')
		this.socket.write(lenBuf)
		this.socket.write(pktBuf)
	}

	private async handleHandshakePacket(packet: HandshakePacket) {
		if (this.isEncrypted) throw new Error(`Already authenticated`)
		if (this.verifyToken) throw new Error(`Encryption already started`)

		this.modVersion = packet.modVersion
		this.gameAddress = packet.gameAddress
		this.claimedMojangName = packet.mojangName
		this.verifyToken = crypto.randomBytes(4)

		this.sendUnencrypted({
			type: 'EncryptionRequest',
			publicKey: this.server.publicKeyBuffer,
			verifyToken: this.verifyToken,
		})
	}

	private async handleEncryptionResponsePacket(pkt: EncryptionResponsePacket) {
		if (this.isEncrypted) throw new Error(`Already authenticated`)
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

		const mojangAuth = await fetchHasJoined({
			username: this.claimedMojangName,
			shaHex,
		})
		if (!mojangAuth?.uuid) throw new Error(`Mojang auth failed`)

		this.uuid = mojangAuth.uuid

		this.cipher = crypto.createCipheriv('aes-128-cfb8', secret, secret)
		this.decipher = crypto.createDecipheriv('aes-128-cfb8', secret, secret)

		this.log('Authenticated as', mojangAuth)
		this.handler.handleClientAuthenticated(this)
	}

	debug(...args: any[]) {
		if (process.env.NODE_ENV === 'production') return
		console.debug(`[Client${this.id}]`, ...args)
	}

	log(...args: any[]) {
		console.log(`[Client${this.id}]`, ...args)
	}

	warn(...args: any[]) {
		console.error(`[Client${this.id}]`, ...args)
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
