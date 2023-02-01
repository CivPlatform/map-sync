import { BufReader } from "./BufReader";
import { BufWriter } from "./BufWriter";
import {
    HandshakePacket,
    EncryptionRequestPacket,
    EncryptionResponsePacket,
    RegionTimestampsPacket,
    RegionCatchupRequestPacket,
    RegionCatchupResponsePacket,
    ChunkCatchupRequestPacket,
    ChunkDataPacket
} from "./packets";
import { inspect } from "util";

export type ClientPacket =
    | ChunkDataPacket
    | EncryptionResponsePacket
    | HandshakePacket
    | ChunkCatchupRequestPacket
    | RegionCatchupRequestPacket;

export type ServerPacket =
    | ChunkDataPacket
    | EncryptionRequestPacket
    | RegionCatchupResponsePacket
    | RegionTimestampsPacket;

export enum Packets {
    "ERROR:pkt0",
    "Handshake",
    "EncryptionRequest",
    "EncryptionResponse",
    "ChunkTile",
    "Catchup",
    "CatchupRequest",
    "RegionTimestamps",
    "RegionCatchup"
}

export function decodePacket(reader: BufReader): ClientPacket {
    const packetID = reader.readUInt8();
    switch (packetID) {
        case Packets.Handshake:
            return HandshakePacket.decode(reader);
        case Packets.EncryptionResponse:
            return EncryptionResponsePacket.decode(reader);
        case Packets.ChunkTile:
            return ChunkDataPacket.decode(reader);
        case Packets.CatchupRequest:
            return ChunkCatchupRequestPacket.decode(reader);
        case Packets.RegionCatchup:
            return RegionCatchupRequestPacket.decode(reader);
        default:
            throw new Error(
                "Unknown server←client packet [" +
                    packetID +
                    ":" +
                    reader.readRemainder().toString("base64") +
                    "]"
            );
    }
}

export function encodePacket(packet: ServerPacket, writer: BufWriter): void {
    const packetID = Packets[packet.type] ?? Packets["ERROR:pkt0"];
    writer.writeUInt8(packetID);
    switch (packetID) {
        case Packets.EncryptionRequest:
            return (packet as EncryptionRequestPacket).encode(writer);
        case Packets.ChunkTile:
            return (packet as ChunkDataPacket).encode(writer);
        case Packets.Catchup:
            return (packet as RegionCatchupResponsePacket).encode(writer);
        case Packets.RegionTimestamps:
            return (packet as RegionTimestampsPacket).encode(writer);
        default:
            throw new Error(
                `Unknown server→client packet [${inspect(packet)}]`
            );
    }
}
