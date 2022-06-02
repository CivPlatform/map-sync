/** Each write advances the internal offset into the buffer.
 * Grows the buffer to twice the current size if a write would exceed the buffer. */
export class BufWriter {

	private offset = 0
	private buffer: Buffer

	constructor(initialSize?: number) {
		this.buffer = Buffer.alloc(initialSize ?? 1024);
	}

	/** Returns a slice reference to the written bytes so far. */
	getBuffer(): Buffer {
		return this.buffer.slice(0, this.offset);
	}

	writeUInt8(value: number) {
		this.ensureSpace(1);
		this.buffer.writeUInt8(value, this.offset);
		this.offset += 1;
	}

	writeInt8(value: number) {
		this.ensureSpace(1);
		this.buffer.writeInt8(value, this.offset);
		this.offset += 1;
	}

	writeUInt16(value: number) {
		this.ensureSpace(2);
		this.buffer.writeUInt16BE(value, this.offset);
		this.offset += 2;
	}

	writeInt16(value: number) {
		this.ensureSpace(2);
		this.buffer.writeInt16BE(value, this.offset);
		this.offset += 2;
	}

	writeUInt32(value: number) {
		this.ensureSpace(4);
		this.buffer.writeUInt32BE(value, this.offset);
		this.offset += 4;
	}

	writeInt32(value: number) {
		this.ensureSpace(4);
		this.buffer.writeInt32BE(value, this.offset);
		this.offset += 4;
	}

	writeUInt64(value: bigint) {
		this.ensureSpace(8);
		this.buffer.writeBigUInt64BE(value, this.offset);
		this.offset += 8;
	}

	writeInt64(value: bigint) {
		this.ensureSpace(8);
		this.buffer.writeBigInt64BE(value, this.offset);
		this.offset += 8;
	}

	/** length-prefixed (32 bits), UTF-8 encoded */
	writeString(value: string) {
		const strBuf = Buffer.from(value, 'utf8');
		this.ensureSpace(4 + strBuf.length);
		this.buffer.writeUInt32BE(strBuf.length, this.offset);
		this.offset += 4;
		this.buffer.set(strBuf, this.offset);
		this.offset += strBuf.length;
	}

	/** length-prefixed (32 bits), UTF-8 encoded */
	writeBufWithLen(buffer: Buffer) {
		this.ensureSpace(4 + buffer.length);
		this.buffer.writeUInt32BE(buffer.length, this.offset);
		this.offset += 4;
		this.buffer.set(buffer, this.offset);
		this.offset += buffer.length;
	}

	writeBufRaw(buf: Buffer) {
		this.ensureSpace(buf.length);
		this.buffer.set(buf, this.offset);
		this.offset += buf.length;
	}

	private ensureSpace(neededBytes: number) {
		let length = this.buffer.length;
		while (length <= this.offset + neededBytes) {
			length = length * 2;
		}
		if (length !== this.buffer.length) {
			const newBuffer = Buffer.alloc(length);
			this.buffer.copy(newBuffer, 0, 0, this.offset);
			this.buffer = newBuffer;
		}
	}

}
