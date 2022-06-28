import { BufReader } from './BufReader';
import { BufWriter } from './BufWriter';

export interface EncryptionRequestPacket {
	type: 'EncryptionRequest';
	publicKey: Buffer;
	verifyToken: Buffer;
}

export namespace EncryptionRequestPacket {
	export function decode(reader: BufReader): EncryptionRequestPacket {
		return {
			type: 'EncryptionRequest',
			publicKey: reader.readBufWithLen(),
			verifyToken: reader.readBufWithLen(),
		};
	}

	export function encode(pkt: EncryptionRequestPacket, writer: BufWriter) {
		writer.writeBufWithLen(pkt.publicKey);
		writer.writeBufWithLen(pkt.verifyToken);
	}
}
