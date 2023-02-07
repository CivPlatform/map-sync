/** Each read advances the internal offset into the buffer. */
export class BufReader {
    private offset = 0;
    private offsetStack: number[] = [];

    constructor(private buf: Buffer) {

    }

    saveOffset() {
        this.offsetStack.push(this.offset);
    }

    restoreOffset() {
        const offset = this.offsetStack.pop();
        if (offset === undefined) {
            throw new Error("Offset stack is empty");
        }
        this.offset = offset;
    }

    readUInt8(): number {
        const value = this.buf.readUInt8(this.offset);
        this.offset += 1;
        return value;
    }

    readInt8(): number {
        const value = this.buf.readInt8(this.offset);
        this.offset += 1;
        return value;
    }

    readUInt16(): number {
        const value = this.buf.readUInt16BE(this.offset);
        this.offset += 2;
        return value;
    }

    readInt16(): number {
        const value = this.buf.readInt16BE(this.offset);
        this.offset += 2;
        return value;
    }

    readUInt32(): number {
        const value = this.buf.readUInt32BE(this.offset);
        this.offset += 4;
        return value;
    }

    readInt32(): number {
        const value = this.buf.readInt32BE(this.offset);
        this.offset += 4;
        return value;
    }

    readUInt64(): bigint {
        const value = this.buf.readBigUInt64BE(this.offset);
        if (value > Number.MAX_SAFE_INTEGER) {
            throw new Error(`64-bit number too big: ${value}`);
        }
        this.offset += 8;
        return value;
    }

    readInt64(): bigint {
        const value = this.buf.readBigInt64BE(this.offset);
        if (value > Number.MAX_SAFE_INTEGER) {
            throw new Error(`64-bit number too big: ${value}`);
        }
        if (value < Number.MIN_SAFE_INTEGER) {
            throw new Error(`64-bit number too small: ${value}`);
        }
        this.offset += 8;
        return value;
    }

    /** length-prefixed (32 bits), UTF-8 encoded */
    readString(): string {
        const length = this.readUInt32();
        const value = this.buf.toString("utf8", this.offset, this.offset + length);
        this.offset += length;
        return value;
    }

    readBufWithLen(): Buffer {
        return this.readBufLen(
            this.readUInt32()
        );
    }

    readBufLen(length: number): Buffer {
        // simply returning a slice() would retain the entire buf in memory
        const buffer = Buffer.allocUnsafe(length);
        this.buf.copy(buffer, 0, this.offset, this.offset + length);
        this.offset += length;
        return buffer;
    }

    /** any reads after this will fail */
    readRemainder(): Buffer {
        return this.readBufLen(this.buf.length - this.offset);
    }
}
