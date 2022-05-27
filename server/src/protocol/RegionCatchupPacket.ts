import { BufReader } from './BufReader'

export interface RegionCatchupPacket {
  type: 'RegionCatchup'
  world: string
  regions: number[]
}

export namespace RegionCatchupPacket {
  export function decode(reader: BufReader): RegionCatchupPacket {
    let world = reader.readString();
    const len = reader.readInt16();
    const regions: number[] = []
    for (let i = 0; i < len; i++) {
      regions.push(reader.readInt16());
      regions.push(reader.readInt16());
    }
    return { type: 'RegionCatchup', world, regions }
  }
}
