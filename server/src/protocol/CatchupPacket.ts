import { BufReader } from './BufReader'
import { BufWriter } from './BufWriter'

export interface CatchupPacket {
    type: 'Catchup'
    lastTimestamps: Buffer;
}

export namespace CatchupPacket {
    export function decode(reader: BufReader): CatchupPacket {
        return {
            type: 'Catchup',
            lastTimestamps: reader.readBufWithLen()
        }
    }

    export function encode(pkt: CatchupPacket, writer: BufWriter) {
        writer.writeBufRaw(pkt.lastTimestamps)
    }
}
