import fs from 'fs'
import tls from 'tls'
import type { ProtocolClient, ProtocolHandler, ServerPacket } from './protocol'
import { decodePacket, encodePacket } from './protocol'
import { BufReader } from './protocol/BufReader'

const { PORT = '12312', HOST = '127.0.0.1' } = process.env

/*
https://nodejs.org/api/tls.html#tlsssl-concepts

create private key:
openssl genrsa -out private-key.pem 4096

create CSR (certificate signing request):
openssl req -new -key private-key.pem -out csr.pem

create public cert by signing private key:
openssl x509 -req -in csr.pem -signkey private-key.pem -out public-cert.pem
*/
const defaultOptions: tls.TlsOptions = {
	key: fs.readFileSync('private-key.pem'),
	cert: fs.readFileSync('public-cert.pem'),
	// rejectUnauthorized: false,
}

export class TlsServer {
	server: tls.Server
	clients: Record<number, TlsClient> = {}

	constructor(options: tls.TlsOptions, handler: ProtocolHandler) {
		options = { ...defaultOptions, ...options }

		this.server = tls.createServer(options, (socket) => {
			const client = new TlsClient(socket, handler)
			this.clients[client.id] = client
			socket.on('end', () => delete this.clients[client.id])
		})

		this.server.on('error', (err: Error) => {
			console.error('[Server] Error:', err)
			this.server.close()
		})

		this.server.listen({ port: PORT, hostname: HOST }, () => {
			console.log('[Server] Listening on', HOST, PORT)
		})
	}
}

let nextClientId = 1

/** Prefixes packets with their length (UInt32BE) */
export class TlsClient implements ProtocolClient {
	readonly id = nextClientId++

	uuid: string | undefined

	/** prevent Out of Memory when client sends a large packet */
	maxFrameSize = 2 ** 24

	constructor(private socket: tls.TLSSocket, handler: ProtocolHandler) {
		this.log('Connected', socket.remoteAddress)
		handler.handleClientConnected(this)

		/** Accumulates received data, containing none, one, or multiple frames; the last frame may be partial only. */
		let accBuf: Buffer = Buffer.alloc(0)

		socket.on('data', (data: Buffer) => {
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
			const pktBuf = frameReader.readBufLen(frameSize)
			accBuf = frameReader.readRemainder()

			const packet = decodePacket(pktBuf)

			handler.handleClientPacketReceived(this, packet)
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
			socket.end()
		})
	}

	send(pkt: ServerPacket) {
		if (!this.socket.writable) return
		const pktBuf = encodePacket(pkt)
		const lenBuf = Buffer.alloc(4)
		lenBuf.writeUInt32BE(pktBuf.length)
		if (!this.socket.writable) return
		this.socket.write(lenBuf)
		this.socket.write(pktBuf)
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
