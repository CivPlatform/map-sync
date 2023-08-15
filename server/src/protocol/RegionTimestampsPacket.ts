import { BufWriter } from './BufWriter'
import { CatchupRegion } from '../model'

export interface RegionTimestampsPacket {
	type: 'RegionTimestamps'
	world: string
	regions: Array<CatchupRegion>
}

export namespace RegionTimestampsPacket {
	export function encode(pkt: RegionTimestampsPacket, writer: BufWriter) {
		writer.writeString(pkt.world)
		writer.writeInt16(pkt.regions.length)
		console.log('Sending regions ' + JSON.stringify(pkt.regions))
		for (let i = 0; i < pkt.regions.length; i++) {
			let region = pkt.regions[i]
			writer.writeInt16(region.regionX)
			writer.writeInt16(region.regionZ)
			writer.writeInt64(region.timestamp)
		}
	}
}
