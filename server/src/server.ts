import crypto from 'crypto'
import net from 'net'
import type { ProtocolClient, ProtocolHandler, ServerPacket } from './protocol'
import { decodePacket, encodePacket } from './protocol'
import { BufReader } from './protocol/BufReader'
import { BufWriter } from './protocol/BufWriter'

const { PORT = '12312', HOST = '127.0.0.1' } = process.env

export class TcpServer {
	server: net.Server
	clients: Record<number, TcpClient> = {}

	serverKey = new NodeRSA({ b: 1024 })

	constructor(handler: ProtocolHandler) {
		this.server = net.createServer({}, (socket) => {
			const client = new TcpClient(socket, handler)
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
}

let nextClientId = 1

/** Prefixes packets with their length (UInt32BE) */
export class TcpClient implements ProtocolClient {
	readonly id = nextClientId++

	verifyToken: Buffer | undefined
	publicKeyBuffer: Buffer | undefined

	uuid: string | undefined

	/** prevent Out of Memory when client sends a large packet */
	maxFrameSize = 2 ** 24

	private cipher?: crypto.Cipher
	private decipher?: crypto.Decipher

	get isEncrypted() {
		return !!(this.cipher && this.decipher)
	}

	constructor(private socket: net.Socket, handler: ProtocolHandler) {
		this.log('Connected', socket.remoteAddress)
		handler.handleClientConnected(this)

		/** Accumulates received data, containing none, one, or multiple frames; the last frame may be partial only. */
		let accBuf: Buffer = Buffer.alloc(0)

		socket.on('data', (data: Buffer) => {
			// TODO what if an exception happens anywhere here?

			this.debug('Received', data.length, 'bytes')

			// creating a new buffer every time is fine in our case, because we expect most frames to be large
			accBuf = Buffer.concat([accBuf, data])
			if (accBuf.length <= 4) return // wait for more data
			const frameSize = accBuf.readUInt32BE()

			// prevent Out of Memory
			if (frameSize > this.maxFrameSize) return socket.end()

			if (accBuf.length < 4 + frameSize) return // wait for more data

			const frameReader = new BufReader(accBuf)
			frameReader.readUInt32() // skip frame size
			let pktBuf = frameReader.readBufLen(frameSize)
			accBuf = frameReader.readRemainder()

			if (this.decipher) {
				pktBuf = this.decipher.update(pktBuf)
			}

			const reader = new BufReader(pktBuf)
			const packet = decodePacket(reader)

			handler.handleClientPacketReceived(this, packet)
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
			socket.end()
		})
	}

	sendUnencrypted(pkt: ServerPacket) {
		if (!this.socket.writable) return
		const writer = new BufWriter() // TODO size hint
		encodePacket(pkt, writer)
		const pktBuf = writer.getBuffer()
		this.frameAndSend(pktBuf)
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

	private frameAndSend(pktBuf: Buffer) {
		const lenBuf = Buffer.alloc(4)
		lenBuf.writeUInt32BE(pktBuf.length)
		if (!this.socket.writable) return
		this.socket.write(lenBuf)
		this.socket.write(pktBuf)
	}

	enableCrypto(secret: Buffer) {
		if (this.cipher || this.decipher) {
			throw new Error('Crypto is already enabled')
		}
		this.cipher = crypto.createCipheriv('aes-128-cfb8', secret, secret)
		this.decipher = crypto.createDecipheriv('aes-128-cfb8', secret, secret)
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
