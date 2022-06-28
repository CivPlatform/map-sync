/** Each read advances the internal offset into the buffer. */
export class BufReader {
	private off = 0;
	private offStack: number[] = [];

	constructor(private buf: Buffer) {}

	saveOffset() {
		this.offStack.push(this.off);
	}

	restoreOffset() {
		const off = this.offStack.pop();
		if (off === undefined) throw new Error('Offset stack is empty');
		this.off = off;
	}

	readUInt8() {
		const val = this.buf.readUInt8(this.off);
		this.off += 1;
		return val;
	}

	readInt8() {
		const val = this.buf.readInt8(this.off);
		this.off += 1;
		return val;
	}

	readUInt16() {
		const val = this.buf.readUInt16BE(this.off);
		this.off += 2;
		return val;
	}

	readInt16() {
		const val = this.buf.readInt16BE(this.off);
		this.off += 2;
		return val;
	}

	readUInt32() {
		const val = this.buf.readUInt32BE(this.off);
		this.off += 4;
		return val;
	}

	readInt32() {
		const val = this.buf.readInt32BE(this.off);
		this.off += 4;
		return val;
	}

	readUInt64() {
		const valBig = this.buf.readBigUInt64BE(this.off);
		if (valBig > Number.MAX_SAFE_INTEGER) {
			throw new Error(`64-bit number too big: ${valBig}`);
		}
		this.off += 8;
		return Number(valBig);
	}

	readInt64() {
		const valBig = this.buf.readBigInt64BE(this.off);
		if (valBig > Number.MAX_SAFE_INTEGER) {
			throw new Error(`64-bit number too big: ${valBig}`);
		}
		if (valBig < Number.MIN_SAFE_INTEGER) {
			throw new Error(`64-bit number too small: ${valBig}`);
		}
		this.off += 8;
		return Number(valBig);
	}

	/** length-prefixed (32 bits), UTF-8 encoded */
	readString() {
		const len = this.readUInt32();
		const str = this.buf.toString('utf8', this.off, this.off + len);
		this.off += len;
		return str;
	}

	readBufWithLen() {
		const len = this.readUInt32();
		return this.readBufLen(len);
	}

	readBufLen(length: number) {
		// simply returning a slice() would retain the entire buf in memory
		const buf = Buffer.allocUnsafe(length);
		this.buf.copy(buf, 0, this.off, this.off + length);
		this.off += length;
		return buf;
	}

	/** any reads after this will fail */
	readRemainder() {
		return this.readBufLen(this.buf.length - this.off);
	}
}
