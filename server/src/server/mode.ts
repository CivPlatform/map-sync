import { inspect } from "node:util";
import { ClientPacket } from "../protocol";

export abstract class AbstractClientMode {
    /**
     * This is called immediately after receiving a buffer from the client. Use
     * this to transform the buffer prior to parsing, such as decrypting.
     */
    public postReceiveBufferTransformer(buffer: Buffer): Promise<Buffer> {
        return Promise.resolve(buffer);
    }
    /**
     * This is called immediately prior to sending a buffer to the client. Use
     * this to transform the buffer, such as encrypting.
     */
    public preSendBufferTransformer(buffer: Buffer): Promise<Buffer> {
        return Promise.resolve(buffer);
    }
    /**
     * Use this to handle incoming packets.
     */
    public abstract onPacketReceived(packet: ClientPacket): Promise<void>;
}

/**
 * Throw this at the bottom of your AbstractClientMode.onPacketReceived if the
 * packet is unsupported.
 */
export class UnsupportedPacketException extends Error {
    public constructor(mode: AbstractClientMode, packet: ClientPacket) {
        super(`ClientMode[${mode.constructor.name}] does not support packet [${packet.type}]: ${inspect(packet)}`);
    }
}
