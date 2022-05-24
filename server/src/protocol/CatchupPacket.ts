import { BufReader } from './BufReader'
import { BufWriter } from './BufWriter'

export interface CatchupPacket {
    type: 'Catchup'
    modVersion: string
    mojangName: string
    gameAddress: string
    lastTimestamps: Buffer;
}

export namespace CatchupPacket {
    export function decode(reader: BufReader): CatchupPacket {
        return {
            type: 'Catchup',
            modVersion: reader.readString(),
            mojangName: reader.readString(),
            gameAddress: reader.readString(),
            lastTimestamps: reader.readBufWithLen()
        }
    }

    export function encode(pkt: CatchupPacket, writer: BufWriter) {
        writer.writeString(pkt.modVersion)
        writer.writeString(pkt.mojangName)
        writer.writeString(pkt.gameAddress)
        writer.writeBufRaw(pkt.lastTimestamps)
    }
}
