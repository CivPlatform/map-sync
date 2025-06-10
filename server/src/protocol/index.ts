import { BufReader } from "./BufReader";
import { BufferWriter } from "./buffers.ts";
import {
    ChunkTilePacket,
    ClientboundEncryptionRequestPacket,
    ClientboundRegionTimestampsPacket,
    ServerboundChunkTimestampsRequestPacket,
    ServerboundEncryptionResponsePacket,
    ServerboundHandshakePacket,
    ClientboundChunkTimestampsResponsePacket,
    ServerboundCatchupRequestPacket,
} from "./packets.ts";

export type ClientPacket =
    | ChunkTilePacket
    | ServerboundEncryptionResponsePacket
    | ServerboundHandshakePacket
    | ServerboundCatchupRequestPacket
    | ServerboundChunkTimestampsRequestPacket;

export type ServerPacket =
    | ChunkTilePacket
    | ClientboundEncryptionRequestPacket
    | ClientboundChunkTimestampsResponsePacket
    | ClientboundRegionTimestampsPacket;

export const packetIds = [
    "ERROR:pkt0",
    ServerboundHandshakePacket.TYPE,
    ClientboundEncryptionRequestPacket.TYPE,
    ServerboundEncryptionResponsePacket.TYPE,
    ChunkTilePacket.TYPE,
    ClientboundChunkTimestampsResponsePacket.TYPE,
    ServerboundCatchupRequestPacket.TYPE,
    ClientboundRegionTimestampsPacket.TYPE,
    ServerboundChunkTimestampsRequestPacket.TYPE,
];

export function getPacketId(type: ServerPacket["type"]) {
    const id = packetIds.indexOf(type);
    if (id === -1) throw new Error(`Unknown packet type ${type.toString()}`);
    return id;
}

export function decodePacket(reader: BufReader): ClientPacket {
    const packetType = reader.readUInt8();
    switch (packetIds[packetType]) {
        case ChunkTilePacket.TYPE:
            return ChunkTilePacket.decode(reader);
        case ServerboundHandshakePacket.TYPE:
            return ServerboundHandshakePacket.decode(reader);
        case ServerboundEncryptionResponsePacket.TYPE:
            return ServerboundEncryptionResponsePacket.decode(reader);
        case ServerboundCatchupRequestPacket.TYPE:
            return ServerboundCatchupRequestPacket.decode(reader);
        case ServerboundChunkTimestampsRequestPacket.TYPE:
            return ServerboundChunkTimestampsRequestPacket.decode(reader);
        default:
            throw new Error(`Unknown packet type ${packetType}`);
    }
}

export function encodePacket(pkt: ServerPacket, writer: BufferWriter): void {
    writer.writeUnt8(getPacketId(pkt.type));
    switch (pkt.type) {
        case ChunkTilePacket.TYPE:
            return (pkt as ChunkTilePacket).encode(writer);
        case ClientboundChunkTimestampsResponsePacket.TYPE:
            return (pkt as ClientboundChunkTimestampsResponsePacket).encode(
                writer,
            );
        case ClientboundEncryptionRequestPacket.TYPE:
            return (pkt as ClientboundEncryptionRequestPacket).encode(writer);
        case ClientboundRegionTimestampsPacket.TYPE:
            return (pkt as ClientboundRegionTimestampsPacket).encode(writer);
        default:
            throw new Error(`Unknown packet type ${(pkt as any).type}`);
    }
}
