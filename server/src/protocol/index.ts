import { BufReader } from "./BufReader";
import { BufWriter } from "./BufWriter";
import { ChunkTilePacket } from "./ChunkTilePacket";
import {
    HandshakePacket,
    EncryptionRequestPacket,
    EncryptionResponsePacket,
    RegionTimestampsPacket
} from "./packets";
import { CatchupPacket } from "./CatchupPacket";
import { CatchupRequestPacket } from "./CatchupRequestPacket";
import { RegionCatchupPacket } from "./RegionCatchupPacket";
import { inspect } from "util";

export type ClientPacket =
    | ChunkTilePacket
    | EncryptionResponsePacket
    | HandshakePacket
    | CatchupRequestPacket
    | RegionCatchupPacket;

export type ServerPacket =
    | ChunkTilePacket
    | EncryptionRequestPacket
    | CatchupPacket
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
            return ChunkTilePacket.decode(reader);
        case Packets.CatchupRequest:
            return CatchupRequestPacket.decode(reader);
        case Packets.RegionCatchup:
            return RegionCatchupPacket.decode(reader);
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
            return ChunkTilePacket.encode(packet as ChunkTilePacket, writer);
        case Packets.Catchup:
            return CatchupPacket.encode(packet as CatchupPacket, writer);
        case Packets.RegionTimestamps:
            return (packet as RegionTimestampsPacket).encode(writer);
        default:
            throw new Error(
                `Unknown server→client packet [${inspect(packet)}]`
            );
    }
}
