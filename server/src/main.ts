import "./cli";
import { connectDB } from "./db";
import { PlayerChunk, PlayerChunkDB } from "./MapChunk";
import { uuid_cache, uuid_cache_save, getConfig, whitelist } from "./metadata";
import { ClientPacket } from "./protocol";
import { TcpClient } from "./server/client";
import { TcpServer } from "./server/server";
import {
    RegionTimestampsPacket,
    RegionCatchupRequestPacket,
    RegionCatchupResponsePacket,
    ChunkCatchupRequestPacket,
    ChunkDataPacket
} from "./protocol/packets";
import { RegionTimestamp } from "./protocol/structs";

export type ProtocolClient = TcpClient; // TODO cleanup
export type ProtocolHandler = Main; // TODO cleanup

// Have to do this because node doesn't have top-level await for CommonJS
Promise.resolve().then(async () => {
    await connectDB();
    const protocolHandler: ProtocolHandler = new Main();
    const server = new TcpServer(protocolHandler);
    protocolHandler.server = server; // TODO: Remove this with handler refactor
});

export class Main {
    server: TcpServer = null!;
}
