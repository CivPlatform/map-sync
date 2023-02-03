import crypto from "node:crypto";
import net from "node:net";
import fetch from "node-fetch";
import { ProtocolHandler } from "../main";
import type { ClientPacket, ServerPacket } from "../protocol";
import { decodePacket, encodePacket } from "../protocol";
import { BufReader } from "../protocol/BufReader";
import { BufWriter } from "../protocol/BufWriter";
import {
    HandshakePacket,
    EncryptionRequestPacket,
    EncryptionResponsePacket
} from "../protocol/packets";
import * as encryption from "./encryption";
import { TcpServer } from "./server";
import { AbstractClientMode, UnsupportedPacketException } from "./mode";
import { MOD_VERSION } from "../const";

/** prevent Out of Memory when client sends a large packet */
const MAX_FRAME_SIZE = 2 ** 24;
let nextClientId = 1;

/** Prefixes packets with their length (UInt32BE);
 * handles Mojang authentication */
export class TcpClient {
    public readonly id = nextClientId++;
    /** contains mojang name once logged in */
    public name = "Client" + this.id;
    public mode: AbstractClientMode = null!;

    public gameAddress: string | undefined;
    public uuid: string | undefined;
    public mcName: string | undefined;
    public world: string | undefined;

    /** sent by client during handshake */
    private claimedMojangName?: string;
    private verifyToken?: Buffer;
    /** we need to wait for the mojang auth response
     * before we can en/decrypt packets following the handshake */
    private cryptoPromise?: Promise<encryption.Ciphers>;

    public constructor(
        public readonly socket: net.Socket,
        public readonly server: TcpServer,
        public readonly handler: ProtocolHandler
    ) {
        /** Accumulates received data, containing none, one, or multiple frames; the last frame may be partial only. */
        let accBuf: Buffer = Buffer.alloc(0);

        socket.on("data", async (data: Buffer) => {
            try {
                if (this.cryptoPromise) {
                    const { decipher } = await this.cryptoPromise;
                    data = decipher.update(data);
                }

                // creating a new buffer every time is fine in our case, because we expect most frames to be large
                accBuf = Buffer.concat([accBuf, data]);

                // we may receive multiple frames in one call
                while (true) {
                    if (accBuf.length <= 4) return; // wait for more data
                    const frameSize = accBuf.readUInt32BE();

                    // prevent Out of Memory
                    if (frameSize > MAX_FRAME_SIZE) {
                        return this.kick(
                            "Frame too large: " +
                                frameSize +
                                " have " +
                                accBuf.length
                        );
                    }

                    if (accBuf.length < 4 + frameSize) return; // wait for more data

                    const frameReader = new BufReader(accBuf);
                    frameReader.readUInt32(); // skip frame size
                    let pktBuf = frameReader.readBufLen(frameSize);
                    accBuf = frameReader.readRemainder();

                    const reader = new BufReader(pktBuf);

                    try {
                        const packet = decodePacket(reader);
                        await this.handlePacketReceived(packet);
                    } catch (err) {
                        this.warn(err);
                        return this.kick("Error in packet handler");
                    }
                }
            } catch (err) {
                this.warn(err);
                return this.kick("Error in data handler");
            }
        });

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

    private setStage0PreAuthMode() {
        const client = this;
        this.mode = new class Stage0PreAuthMode extends AbstractClientMode {
            async onPacketReceived(packet: ClientPacket) {
                if (packet instanceof HandshakePacket) {
                    // TODO: Uncomment this when the version is correctly
                    //       filtered. Currently, the "packet.modVersion" will
                    //       return "${version}+fabric" or similar.
                    // if (packet.modVersion !== MOD_VERSION) {
                    //     client.log(`Kicking for unsupported mod version [${packet.modVersion}]`);
                    //     client.kick("Unsupported mod version!");
                    //     return;
                    // }
                    client.gameAddress = packet.gameAddress;
                    // TODO: Likewise, maybe disconnect here if the gameAddress
                    //       isn't supported
                    client.claimedMojangName = packet.mojangName;
                    client.world = packet.world;
                    client.verifyToken = crypto.randomBytes(4);
                    await client.INTERNAL_send(
                        new EncryptionRequestPacket(
                            encryption.PUBLIC_KEY_BUFFER,
                            client.verifyToken
                        )
                    );
                    // TODO: Set stage 1 mode here
                }
                throw new UnsupportedPacketException(this, packet);
            }
        };
    }

    private setStage1PreAuthMode() {
        const client = this;
        this.mode = new class Stage1PreAuthMode extends AbstractClientMode {
            async onPacketReceived(packet: ClientPacket) {
                if (packet instanceof EncryptionResponsePacket) {
                    const verifyToken = encryption.decrypt(packet.verifyToken);
                    if (!verifyToken.equals(client.verifyToken!)) {
                        throw new Error(
                            `verifyToken mismatch: got ${verifyToken} expected ${client.verifyToken}`
                        );
                    }
                    const sharedSecret = encryption.decrypt(packet.sharedSecret);
                    const mojangAuth = await fetchHasJoined({
                        username: client.claimedMojangName!,
                        shaHex: encryption.generateShaHex(sharedSecret)
                    });
                    if (!mojangAuth?.uuid) {
                        client.kick(`Mojang auth failed`);
                        throw new Error(`Mojang auth failed`);
                    }
                    client.log("Authenticated as", mojangAuth);
                    client.uuid = mojangAuth.uuid;
                    client.mcName = mojangAuth.name;
                    client.name += ":" + mojangAuth.name;
                    client.cryptoPromise = Promise.resolve(encryption.generateCiphers(sharedSecret));
                    await client.cryptoPromise.then(async () => {
                        await client.handler.handleClientAuthenticated(client);
                    });
                    // TODO: Set authed mode here
                }
                throw new UnsupportedPacketException(this, packet);
            }
        };
    }

    private async handlePacketReceived(pkt: ClientPacket) {
        if (!this.uuid) {
            // not authenticated yet
            switch (pkt.type) {
                case "Handshake":
                    this.setStage0PreAuthMode();
                    await this.mode.onPacketReceived(pkt);
                    return;
                case "EncryptionResponse":
                    this.setStage1PreAuthMode();
                    await this.mode.onPacketReceived(pkt);
                    return;
            }
            throw new Error(
                `Packet ${pkt.type} from unauth'd client ${this.id}`
            );
        } else {
            return await this.handler.handleClientPacketReceived(this, pkt);
        }
    }

    public kick(internalReason: string) {
        this.log(`Kicking:`, internalReason);
        this.socket.destroy();
    }

    public async send(pkt: ServerPacket) {
        if (!this.cryptoPromise) {
            this.debug("Not encrypted, dropping packet", pkt.type);
            return;
        }
        if (!this.uuid) {
            this.debug("Not authenticated, dropping packet", pkt.type);
            return;
        }
        this.debug(this.mcName + " -> " + pkt.type);
        await this.INTERNAL_send(pkt, true);
    }

    private async INTERNAL_send(pkt: ServerPacket, doCrypto = false) {
        if (!this.socket.writable)
            return this.debug("Socket closed, dropping", pkt.type);
        if (doCrypto && !this.cryptoPromise)
            throw new Error(`Can't encrypt: handshake not finished`);

        const writer = new BufWriter(); // TODO size hint
        writer.writeUInt32(0); // set later, but reserve space in buffer
        encodePacket(pkt, writer);
        let buf = writer.getBuffer();
        buf.writeUInt32BE(buf.length - 4, 0); // write into space reserved above

        if (doCrypto) {
            const { encipher } = await this.cryptoPromise!;
            buf = encipher.update(buf);
        }

        this.socket.write(buf);
    }

    public debug(...args: any[]) {
        if (process.env.NODE_ENV === "production") return;
        console.debug(`[${this.name}]`, ...args);
    }

    public log(...args: any[]) {
        console.log(`[${this.name}]`, ...args);
    }

    public warn(...args: any[]) {
        console.error(`[${this.name}]`, ...args);
    }
}

async function fetchHasJoined(args: {
    username: string;
    shaHex: string;
    clientIp?: string;
}) {
    const { username, shaHex, clientIp } = args;
    let url = `https://sessionserver.mojang.com/session/minecraft/hasJoined?username=${username}&serverId=${shaHex}`;
    if (clientIp) url += `&ip=${clientIp}`;
    const res = await fetch(url);
    try {
        if (res.status === 204) return null;
        let { id, name } = (await res.json()) as { id: string; name: string };
        const uuid = id.replace(
            /^(........)-?(....)-?(....)-?(....)-?(............)$/,
            "$1-$2-$3-$4-$5"
        );
        return { uuid, name };
    } catch (err) {
        console.error(res);
        throw err;
    }
}
