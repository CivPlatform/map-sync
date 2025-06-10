import { BufReader } from "./BufReader";

export interface HandshakePacket {
    type: "Handshake";
    modVersion: string;
    mojangName: string;
    gameAddress: string;
    dimension: string;
}

export namespace HandshakePacket {
    export function decode(reader: BufReader): HandshakePacket {
        return {
            type: "Handshake",
            modVersion: reader.readString(),
            mojangName: reader.readString(),
            gameAddress: reader.readString(),
            dimension: reader.readString(),
        };
    }
}
