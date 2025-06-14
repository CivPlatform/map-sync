import { BufferWriter, BufferReader } from "./buffers.ts";
import {
    ChunkTilePacket,
    ClientboundAuthRequestPacket,
    ClientboundRegionTimestampsPacket,
    ServerboundChunkTimestampsRequestPacket,
    ServerboundAuthResponsePacket,
    ServerboundHandshakePacket,
    ClientboundChunkTimestampsResponsePacket,
    ServerboundCatchupRequestPacket,
    ClientboundWelcomePacket,
} from "./packets.ts";

export type ClientPacket =
    | ChunkTilePacket
    | ServerboundAuthResponsePacket
    | ServerboundHandshakePacket
    | ServerboundCatchupRequestPacket
    | ServerboundChunkTimestampsRequestPacket;

export type ServerPacket =
    | ChunkTilePacket
    | ClientboundAuthRequestPacket
    | ClientboundChunkTimestampsResponsePacket
    | ClientboundRegionTimestampsPacket
    | ClientboundWelcomePacket;

export const packetIds = [
    "ERROR:pkt0",
    ServerboundHandshakePacket.TYPE,
    ClientboundAuthRequestPacket.TYPE,
    ServerboundAuthResponsePacket.TYPE,
    ChunkTilePacket.TYPE,
    ClientboundChunkTimestampsResponsePacket.TYPE,
    ServerboundCatchupRequestPacket.TYPE,
    ClientboundRegionTimestampsPacket.TYPE,
    ServerboundChunkTimestampsRequestPacket.TYPE,
    ClientboundWelcomePacket.TYPE,
];

export function getPacketId(type: ServerPacket["type"]) {
    const id = packetIds.indexOf(type);
    if (id <= 0) throw new Error(`Unknown packet type ${type.toString()}`);
    return id;
}

export function decodePacket(reader: BufferReader): ClientPacket {
    const packetType = reader.readUnt8();
    switch (packetIds[packetType]) {
        case ChunkTilePacket.TYPE:
            return ChunkTilePacket.decode(reader);
        case ServerboundHandshakePacket.TYPE:
            return ServerboundHandshakePacket.decode(reader);
        case ServerboundAuthResponsePacket.TYPE:
            return ServerboundAuthResponsePacket.decode(reader);
        case ServerboundCatchupRequestPacket.TYPE:
            return ServerboundCatchupRequestPacket.decode(reader);
        case ServerboundChunkTimestampsRequestPacket.TYPE:
            return ServerboundChunkTimestampsRequestPacket.decode(reader);
        default:
            throw new Error(`Unknown packet type ${packetType}`);
    }
}

export function encodePacket(packet: ServerPacket, writer: BufferWriter): void {
    writer.writeUnt8(getPacketId(packet.type));
    switch (packet.type) {
        case ChunkTilePacket.TYPE:
            return (packet as ChunkTilePacket).encode(writer);
        case ClientboundChunkTimestampsResponsePacket.TYPE:
            return (packet as ClientboundChunkTimestampsResponsePacket).encode(
                writer,
            );
        case ClientboundAuthRequestPacket.TYPE:
            return (packet as ClientboundAuthRequestPacket).encode(writer);
        case ClientboundWelcomePacket.TYPE:
            return (packet as ClientboundWelcomePacket).encode(writer);
        case ClientboundRegionTimestampsPacket.TYPE:
            return (packet as ClientboundRegionTimestampsPacket).encode(writer);
        default:
            throw new Error(`Unknown packet type ${(packet as any).type}`);
    }
}

export function encodePacketToBytes(packet: ServerPacket): Buffer {
    const writer = new BufferWriter();
    encodePacket(packet, writer);
    return writer.getBuffer();
}

export class UnexpectedPacket extends Error {
    public constructor(message?: string) {
        super(message);
    }
}
