import crypto, { KeyPairKeyObjectResult } from "crypto";
import net from "net";
import fetch from "node-fetch";
import { Main } from "./main";
import type { ClientPacket, ServerPacket } from "./protocol";
import { decodePacket, encodePacket } from "./protocol";
import { BufReader } from "./protocol/BufReader";
import { BufWriter } from "./protocol/BufWriter";
import { EncryptionResponsePacket } from "./protocol/EncryptionResponsePacket";
import { HandshakePacket } from "./protocol/HandshakePacket";

// ============================================================ //
// Server
// ============================================================ //

/** prevent Out of Memory when client sends a large packet */
const MAX_FRAME_SIZE = 2 ** 24; // 16MB

type ProtocolHandler = Main // TODO cleanup
/**
 * Server class that, when constructed, will set up a TCP server that will listen, by default, on "127.0.0.1:12312" but
 * will defer to "HOST" and "PORT" environment variables.
 */
export class TcpServer {

	public readonly server: net.Server;
	public readonly clients: Map<number, TcpClient> = new Map();
	private nextClientId: number = 0;

	constructor(
		public readonly handler: ProtocolHandler,
	) {
		this.server = net.createServer({}, (socket: net.Socket) => {
			const client = new TcpClient(++this.nextClientId, socket, this, this.handler);
			this.clients.set(client.id, client);
			socket.on("end", () => this.clients.delete(client.id));
			socket.on("close", () => this.clients.delete(client.id));
		});
		this.server.on("error", (err: Error) => {
			console.error("[TcpServer] Error:", err);
			this.server.close();
		});
		const { PORT = "12312", HOST = "127.0.0.1" } = process.env;
		this.server.listen({ port: PORT, hostname: HOST }, () => {
			console.log("[TcpServer] Listening on " + HOST + ":" + PORT);
		});
	}

}

// ============================================================ //
// Crypto
// ============================================================ //

const KEY_PAIR: KeyPairKeyObjectResult = crypto.generateKeyPairSync("rsa", { modulusLength: 1024 });
const PUBLIC_KEY_BUFFER: Buffer = KEY_PAIR.publicKey.export({ type: "spki", format: "der" });

function decryptBuffer(buffer: Buffer): Buffer {
	return crypto.privateDecrypt({
		key: KEY_PAIR.privateKey,
		padding: crypto.constants.RSA_PKCS1_PADDING
	}, buffer);
}

// ============================================================ //
// Clients
// ============================================================ //

/**
 * Prefixes packets with their length (UInt32BE) and handles Mojang-authentication
 */
export class TcpClient {

	// contains Mojang-name post-authentication
	public name: string;
	public modVersion?: string;
	public gameAddress?: string;
	public uuid?: string;
	public mcName?: string;
	public world?: string;
	public whitelisted?: boolean;
	// sent by client during handshake
	private claimedMojangName?: string;
	private verifyToken?: Buffer;
	// we need to wait for the mojang auth response before we can [en/de]crypt packets following the handshake
	private cryptoPromise?: Promise<{
		cipher: crypto.Cipher
		decipher: crypto.Decipher
	}>;

	constructor(
		public readonly id: number,
		private readonly socket: net.Socket,
		private readonly server: TcpServer,
		private readonly handler: ProtocolHandler,
	) {
		this.name = "Client" + this.id;
		this.log("Connected from", socket.remoteAddress);
		this.handler.handleClientConnected(this);
		// Accumulates received data, containing none, one, or multiple frames; the last frame may be partial only.
		let accumulateBuffer: Buffer = Buffer.alloc(0);
		this.socket.on("data", async (data: Buffer) => {
			try {
				if (this.cryptoPromise) {
					const { decipher } = await this.cryptoPromise;
					data = decipher.update(data);
				}
				// creating a new buffer every time is fine in our case, because we expect most frames to be large
				accumulateBuffer = Buffer.concat([accumulateBuffer, data]);
				// we may receive multiple frames in one call
				while (true) {
					if (accumulateBuffer.length <= 4) {
						// wait for more data
						return;
					}
					const frameSize = accumulateBuffer.readUInt32BE();
					// prevent Out of Memory
					if (frameSize > MAX_FRAME_SIZE) {
						this.kick("Frame too large: " + frameSize + " have " + accumulateBuffer.length);
						return;
					}
					if (accumulateBuffer.length < 4 + frameSize) {
						// wait for more data
						return;
					}
					const frameReader = new BufReader(accumulateBuffer);
					frameReader.readUInt32(); // skip frame size
					const packetBuffer = frameReader.readBufLen(frameSize);
					accumulateBuffer = frameReader.readRemainder();
					const packetReader = new BufReader(packetBuffer);
					try {
						const packet = decodePacket(packetReader);
						await this.handlePacketReceived(packet);
					}
					catch (err) {
						this.warn(err);
						this.kick("Error in packet handler");
						return;
					}
				}
			}
			catch (err) {
				this.warn(err);
				this.kick("Error in data handler");
				return;
			}
		})
		socket.on("close", (hadError: boolean) => {
			this.log("Closed.", { hadError });
		});
		socket.on("end", () => {
			this.log("Ended");
		});
		socket.on("timeout", () => {
			this.warn("Timeout");
		});
		socket.on("error", (err: Error) => {
			this.warn("Error:", err);
			this.kick("Socket error");
		});
	}

	/**
	 * Forcibly disconnects the client.
	 *
	 * @param internalReason The reason for the disconnect. This is NOT sent to the client.
	 */
	public kick(internalReason: string) {
		this.log("Kicking:", internalReason);
		this.socket.destroy();
	}

	/**
	 * Sends a packet to the client.
	 *
	 * @param packet The packet to send.
	 */
	public async send(packet: ServerPacket) {
		if (!this.cryptoPromise) {
			this.debug("Not encrypted, dropping packet", packet.type);
			return;
		}
		if (!this.uuid) {
			this.debug("Not authenticated, dropping packet", packet.type);
			return;
		}
		if (packet.type !== "ChunkTile") {
			this.debug(this.mcName + " -> " + packet.type);
		}
		await this.INTERNAL_send(packet, true);
	}

	private async INTERNAL_send(packet: ServerPacket, doCrypto: boolean = false) {
		if (!this.socket.writable) {
			this.debug("Socket closed, dropping", packet.type);
			return;
		}
		if (doCrypto && !this.cryptoPromise) {
			throw new Error("Can't encrypt: handshake not finished");
		}
		const packetWriter = new BufWriter(); // TODO: size hint (Ori: presumably means reducing unnecessary expansions)
		packetWriter.writeUInt32(0); // set later, but reserve space in buffer
		encodePacket(packet, packetWriter);
		let packetBuffer = packetWriter.getBuffer();
		packetBuffer.writeUInt32BE(packetBuffer.length - 4, 0); // write into space reserved above
		if (doCrypto) {
			const { cipher } = await this.cryptoPromise!;
			packetBuffer = cipher!.update(packetBuffer);
		}
		this.socket.write(packetBuffer);
	}

	private async handlePacketReceived(packet: ClientPacket) {
		if (!this.uuid) {
			// not authenticated yet
			switch (packet.type) {
				case "Handshake":
					await this.handleHandshakePacket(packet);
					break;
				case "EncryptionResponse":
					await this.handleEncryptionResponsePacket(packet);
					break;
				default:
					throw new Error(`Packet ${packet.type} from unauthenticated client ${this.id}`);
			}
		}
		else {
			await this.handler.handleClientPacketReceived(this, packet);
		}
	}

	private async handleHandshakePacket(packet: HandshakePacket) {
		if (this.cryptoPromise) {
			throw new Error("Already authenticated");
		}
		if (this.verifyToken) {
			throw new Error("Encryption already started");
		}
		this.modVersion = packet.modVersion;
		this.gameAddress = packet.gameAddress;
		this.claimedMojangName = packet.mojangName;
		this.world = packet.world;
		this.verifyToken = crypto.randomBytes(4);
		await this.INTERNAL_send({
			type: "EncryptionRequest",
			publicKey: PUBLIC_KEY_BUFFER,
			verifyToken: this.verifyToken
		});
	}

	private async handleEncryptionResponsePacket(packet: EncryptionResponsePacket) {
		if (this.cryptoPromise) {
			throw new Error("Already authenticated");
		}
		if (!this.claimedMojangName) {
			throw new Error("Encryption has not started: no mojangName");
		}
		if (!this.verifyToken) {
			throw new Error("Encryption has not started: no verifyToken");
		}
		const verifyToken = decryptBuffer(packet.verifyToken);
		if (!this.verifyToken.equals(verifyToken)) {
			throw new Error(`verifyToken mismatch: got [${verifyToken}] but expected [${this.verifyToken}]`);
		}
		const secret = decryptBuffer(packet.sharedSecret);
		const shaHex = crypto
			.createHash("sha1")
			.update(secret)
			.update(PUBLIC_KEY_BUFFER)
			.digest()
			.toString("hex");
		this.cryptoPromise = fetchHasJoined(this.claimedMojangName, shaHex)
			.then(async (mojangAuth) => {
				if (!mojangAuth?.uuid) {
					throw new Error("Mojang auth failed");
				}
				this.log("Authenticated as", mojangAuth);
				this.uuid = mojangAuth.uuid;
				this.mcName = mojangAuth.name;
				this.name += ":" + mojangAuth.name;
				return {
					cipher: crypto.createCipheriv("aes-128-cfb8", secret, secret),
					decipher: crypto.createDecipheriv("aes-128-cfb8", secret, secret)
				};
			});
		await this.cryptoPromise.then(async () => {
			await this.handler.handleClientAuthenticated(this);
		});
	}

	public debug(...args: any[]) {
		if (process.env.NODE_ENV !== "production") {
			console.debug(`[${this.name}] [DEBUG]`, ...args);
		}
	}

	public log(...args: any[]) {
		console.log(`[${this.name}] [INFO]`, ...args);
	}

	public warn(...args: any[]) {
		console.error(`[${this.name}] [WARN]`, ...args);
	}

}

/**
 * Attempts to authenticate the user with Mojang, gaining access to some information about the user.
 *
 * @param username The Minecraft-name the client claims to be.
 * @param shaHex The deterministic SHA based on the server's public key and the client's shared secret.
 * @param clientIp The client's IP if you wish to check whether the client's IP matches their Minecraft client's IP.
 * @return Returns the user's details (Minecraft name + uuid), or null if the user could not be authenticated.
 *
 * @see https://wiki.vg/Protocol_Encryption#Server
 */
async function fetchHasJoined(
	username: string,
	shaHex: string,
	clientIp?: string
): Promise<{ uuid: string, name: string } | null> {
	let url = `https://sessionserver.mojang.com/session/minecraft/hasJoined?username=${username}&serverId=${shaHex}`;
	if (clientIp) {
		url += `&ip=${clientIp}`;
	}
	const res = await fetch(url);
	try {
		if (res.status === 204) {
			return null;
		}
		const { id, name } = (await res.json()) as { id: string; name: string };
		const uuid = id.replace(
			/^(........)-?(....)-?(....)-?(....)-?(............)$/,
			"$1-$2-$3-$4-$5",
		);
		return { uuid, name };
	}
	catch (err) {
		console.error("Mojang hasJoined parsing error; res:", res);
		throw err;
	}
}
