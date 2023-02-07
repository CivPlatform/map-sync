/** Each write advances the internal offset into the buffer.
 * Grows the buffer to twice the current size if a write would exceed the buffer. */
export class BufWriter {
    private offset = 0;
    private buffer: Buffer;

    constructor(initialSize?: number) {
        this.buffer = Buffer.alloc(initialSize ?? 1024);
    }

    /** Returns a slice reference to the written bytes so far. */
    getBuffer(): Buffer {
        return this.buffer.subarray(0, this.offset);
    }

    writeUInt8(value: number): this {
        this.ensureSpace(1);
        this.buffer.writeUInt8(value, this.offset);
        this.offset += 1;
        return this;
    }

    writeInt8(value: number): this {
        this.ensureSpace(1);
        this.buffer.writeInt8(value, this.offset);
        this.offset += 1;
        return this;
    }

    writeUInt16(value: number): this {
        this.ensureSpace(2);
        this.buffer.writeUInt16BE(value, this.offset);
        this.offset += 2;
        return this;
    }

    writeInt16(value: number): this {
        this.ensureSpace(2);
        this.buffer.writeInt16BE(value, this.offset);
        this.offset += 2;
        return this;
    }

    writeUInt32(value: number): this {
        this.ensureSpace(4);
        this.buffer.writeUInt32BE(value, this.offset);
        this.offset += 4;
        return this;
    }

    writeInt32(value: number): this {
        this.ensureSpace(4);
        this.buffer.writeInt32BE(value, this.offset);
        this.offset += 4;
        return this;
    }

    writeUInt64(value: bigint | number): this {
        this.ensureSpace(8);
        this.buffer.writeBigUInt64BE(BigInt(value), this.offset);
        this.offset += 8;
        return this;
    }

    writeInt64(value: bigint | number): this {
        this.ensureSpace(8);
        this.buffer.writeBigInt64BE(BigInt(value), this.offset);
        this.offset += 8;
        return this;
    }

    /** length-prefixed (32 bits), UTF-8 encoded */
    writeString(value: string): this {
        const stringBuffer = Buffer.from(value, "utf8");
        this.ensureSpace(4 + stringBuffer.length);
        this.buffer.writeUInt32BE(stringBuffer.length, this.offset);
        this.offset += 4;
        this.buffer.set(stringBuffer, this.offset);
        this.offset += stringBuffer.length;
        return this;
    }

    /** length-prefixed (32 bits), UTF-8 encoded */
    writeBufWithLen(buffer: Buffer): this {
        this.ensureSpace(4 + buffer.length);
        this.buffer.writeUInt32BE(buffer.length, this.offset);
        this.offset += 4;
        this.buffer.set(buffer, this.offset);
        this.offset += buffer.length;
        return this;
    }

    writeBufRaw(buffer: Buffer): this {
        this.ensureSpace(buffer.length);
        this.buffer.set(buffer, this.offset);
        this.offset += buffer.length;
        return this;
    }

    private ensureSpace(bytes: number) {
        let length = this.buffer.length;
        while (length <= this.offset + bytes) {
            length = length * 2;
        }
        if (length !== this.buffer.length) {
            const newBuffer = Buffer.alloc(length);
            this.buffer.copy(newBuffer, 0, 0, this.offset);
            this.buffer = newBuffer;
        }
    }
}
