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
		for (let i = 0; i < pkt.regions.length; /* no increment */){
			let region = pkt.regions[i];
			let regionX : number = region.region_x;
			writer.writeInt16(regionX);
			writer.writeInt16(region.region_z);

			let count: number = 1;
			for (let j = i + 1; j <= pkt.regions.length; j++) {
				if (j == pkt.regions.length || pkt.regions[j].region_x !== regionX) {
					count = j - i;
					break;
				}
			}

			writer.writeInt16(count);

			for (let j = i; j < i + count; j++) {
				writer.writeInt64(pkt.regions[j].ts);
			}

			i += count;
		}
	}
}
