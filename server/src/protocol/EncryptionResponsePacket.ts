import { BufReader } from './BufReader';
import { BufWriter } from './BufWriter';

export interface EncryptionResponsePacket {
	type: 'EncryptionResponse';
	/** encrypted with server's public key */
	sharedSecret: Buffer;
	/** encrypted with server's public key */
	verifyToken: Buffer;
}

export namespace EncryptionResponsePacket {
	export function decode(reader: BufReader): EncryptionResponsePacket {
		return {
			type: 'EncryptionResponse',
			sharedSecret: reader.readBufWithLen(),
			verifyToken: reader.readBufWithLen(),
		};
	}

	export function encode(pkt: EncryptionResponsePacket, writer: BufWriter) {
		writer.writeBufWithLen(pkt.sharedSecret);
		writer.writeBufWithLen(pkt.verifyToken);
	}
}
