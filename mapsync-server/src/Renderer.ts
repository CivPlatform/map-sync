import { spawn } from "child_process";
import { promisify } from "util";
import * as database from "./database";

export async function renderTile(
    dimension: string,
    tileX: number,
    tileZ: number,
) {
    const allChunks = await database.getRegionChunks(dimension, tileX, tileZ);

    const proc = spawn(
        "../render/target/release/civmap-render",
        [String(tileX), String(tileZ), "tiles"],
        { cwd: "../render" }, // so render can find blocks.json
    );
    proc.stdout.pipe(process.stdout);
    proc.stderr.pipe(process.stderr);

    const write = promisify<Buffer, void>(proc.stdin.write.bind(proc.stdin));

    const numBuf = Buffer.allocUnsafe(4);
    numBuf.writeUInt32BE(allChunks.length);
    await write(numBuf);

    const chunkHeaderBuf = Buffer.allocUnsafe(4 + 4 + 2); // reused. 32+32+16 bit
    for (const chunk of allChunks) {
        chunkHeaderBuf.writeInt32BE(chunk.chunk_x, 0);
        chunkHeaderBuf.writeInt32BE(chunk.chunk_z, 4);
        chunkHeaderBuf.writeUInt16BE(chunk.version, 8);
        await write(chunkHeaderBuf);
        await write(chunk.data);
    }
}
