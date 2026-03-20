/** Each write advances the internal offset into the buffer.
 * Grows the buffer to twice the current size if a write would exceed the buffer. */
export class BufWriter {
    private off = 0;
    private buf: Buffer;

    constructor(initialSize?: number) {
        this.buf = Buffer.alloc(initialSize || 1024);
    }

    /** Returns a slice reference to the written bytes so far. */
    getBuffer() {
        return this.buf.slice(0, this.off);
    }

    writeUInt8(val: number) {
        this.ensureSpace(1);
        this.buf.writeUInt8(val, this.off);
        this.off += 1;
    }

    writeInt8(val: number) {
        this.ensureSpace(1);
        this.buf.writeInt8(val, this.off);
        this.off += 1;
    }

    writeUInt16(val: number) {
        this.ensureSpace(2);
        this.buf.writeUInt16BE(val, this.off);
        this.off += 2;
    }

    writeInt16(val: number) {
        this.ensureSpace(2);
        this.buf.writeInt16BE(val, this.off);
        this.off += 2;
    }

    writeUInt32(val: number) {
        this.ensureSpace(4);
        this.buf.writeUInt32BE(val, this.off);
        this.off += 4;
    }

    writeInt32(val: number) {
        this.ensureSpace(4);
        this.buf.writeInt32BE(val, this.off);
        this.off += 4;
    }

    writeUInt64(val: number) {
        this.ensureSpace(8);
        this.buf.writeBigUInt64BE(BigInt(val), this.off);
        this.off += 8;
    }

    writeInt64(val: number) {
        this.ensureSpace(8);
        this.buf.writeBigInt64BE(BigInt(val), this.off);
        this.off += 8;
    }

    /** length-prefixed (32 bits), UTF-8 encoded */
    writeString(str: string) {
        const strBuf = Buffer.from(str, "utf8");
        this.ensureSpace(4 + strBuf.length);
        this.buf.writeUInt32BE(strBuf.length, this.off);
        this.off += 4;
        this.buf.set(strBuf, this.off);
        this.off += strBuf.length;
    }

    /** length-prefixed (32 bits), UTF-8 encoded */
    writeBufWithLen(buf: Buffer) {
        this.ensureSpace(4 + buf.length);
        this.buf.writeUInt32BE(buf.length, this.off);
        this.off += 4;
        this.buf.set(buf, this.off);
        this.off += buf.length;
    }

    writeBufRaw(buf: Buffer) {
        this.ensureSpace(buf.length);
        this.buf.set(buf, this.off);
        this.off += buf.length;
    }

    private ensureSpace(bytes: number) {
        let len = this.buf.length;
        while (len <= this.off + bytes) {
            len = len * 2;
        }
        if (len !== this.buf.length) {
            const newBuf = Buffer.alloc(len);
            this.buf.copy(newBuf, 0, 0, this.off);
            this.buf = newBuf;
        }
    }
}
