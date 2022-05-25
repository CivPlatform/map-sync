import { BufReader } from './BufReader'
import { BufWriter } from './BufWriter'

export interface CatchupRequestPacket {
    type: 'CatchupRequest'
    world: string
    chunk_x: number
    chunk_z: number
    timestamp: number
}

export namespace CatchupRequestPacket {
    export function decode(reader: BufReader): CatchupRequestPacket {
        return {
            type: 'CatchupRequest',
            world: reader.readString(),
            chunk_x: reader.readInt32(),
            chunk_z: reader.readInt32(),
            timestamp: reader.readUInt64()
        }
    }

    export function encode(pkt: CatchupRequestPacket, writer: BufWriter) {
        writer.writeString(pkt.world)
        writer.writeInt32(pkt.chunk_x)
        writer.writeInt32(pkt.chunk_z)
        writer.writeUInt64(pkt.timestamp)
    }
}