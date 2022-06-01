/** Each read advances the internal offset into the buffer. */
export class BufReader {

	private offset = 0;
	private offStack: number[] = [];

	constructor(private buffer: Buffer) {

	}

	saveOffset() {
		this.offStack.push(this.offset);
	}

	restoreOffset() {
		const off = this.offStack.pop();
		if (off === undefined) {
			throw new Error('Offset stack is empty');
		}
		this.offset = off;
	}

	readUInt8(): number {
		try {
			return this.buffer.readUInt8(this.offset);
		}
		finally {
			this.offset += 1;
		}
	}

	readInt8(): number {
		try {
			return this.buffer.readInt8(this.offset);
		}
		finally {
			this.offset += 1;
		}
	}

	readUInt16(): number {
		try {
			return this.buffer.readUInt16BE(this.offset);
		}
		finally {
			this.offset += 2;
		}
	}

	readInt16(): number {
		try {
			return this.buffer.readInt16BE(this.offset);
		}
		finally {
			this.offset += 2;
		}
	}

	readUInt32(): number {
		try {
			return this.buffer.readUInt32BE(this.offset);
		}
		finally {
			this.offset += 4;
		}
	}

	readInt32(): number {
		try {
			return this.buffer.readInt32BE(this.offset);
		}
		finally {
			this.offset += 4;
		}
	}

	readUInt64(): bigint {
		try {
			return this.buffer.readBigUInt64BE(this.offset);
		}
		finally {
			this.offset += 8;
		}
	}

	readInt64(): bigint {
		try {
			return this.buffer.readBigInt64BE(this.offset);
		}
		finally {
			this.offset += 8;
		}
	}

	/** length-prefixed (32 bits), UTF-8 encoded */
	readString(): string {
		const length = this.readUInt32();
		try {
			return this.buffer.toString('utf8', this.offset, this.offset + length);
		}
		finally {
			this.offset += length;
		}
	}

	readBufWithLen(): Buffer {
		const length = this.readUInt32();
		return this.readBufLen(length);
	}

	readBufLen(length: number): Buffer {
		// simply returning a slice() would retain the entire buffer in memory
		const buffer: Buffer = Buffer.allocUnsafe(length);
		this.buffer.copy(buffer, 0, this.offset, this.offset + length);
		this.offset += length;
		return buffer;
	}

	/** any reads after this will fail */
	readRemainder(): Buffer {
		return this.readBufLen(this.buffer.length - this.offset);
	}

}
