import { BufWriter } from './BufWriter'

export interface RegionTimestampsPacket {
	type: 'RegionTimestamps'
	world: string
	regions: any[]
}

export namespace RegionTimestampsPacket {
	export function encode(pkt: RegionTimestampsPacket, writer: BufWriter) {
		writer.writeString(pkt.world);
		writer.writeInt16(pkt.regions.length);
		console.log("Sending regions " + JSON.stringify(pkt.regions))
		for (let i = 0; i < pkt.regions.length; i++){
			let region = pkt.regions[i];
			writer.writeInt16(region.region_x);
			writer.writeInt16(region.region_z);

			writer.writeInt64(pkt.regions[i].ts);
		}
	}
}
