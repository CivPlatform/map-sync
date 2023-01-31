import { BufReader } from "./BufReader";
import { Packets } from "./index";

/**
 * The Minecraft client should send this packet IMMEDIATELY upon a successful
 * connection to the MapSync server.
 */
export class HandshakePacket {
    public readonly type: string = Packets[Packets.Handshake];

    /**
     * @param modVersion The MapSync version (effectively the protocol version)
     * @param mojangName The client's Mojang username (not their email)
     * @param gameAddress The server-address for the Minecraft server they're
     *                    connected to.
     * @param world The dimension the client is in.
     */
    public constructor(
        public readonly modVersion: string,
        public readonly mojangName: string,
        public readonly gameAddress: string,
        public readonly world: string
    ) { }

    public static decode(reader: BufReader): HandshakePacket {
        return new HandshakePacket(
            reader.readString(),
            reader.readString(),
            reader.readString(),
            reader.readString()
        );
    }
}
