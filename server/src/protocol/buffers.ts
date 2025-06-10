import { ArrayBufferSink } from "bun";

export class BufferWriter {
    private readonly sink: ArrayBufferSink;
    private readonly view = new DataView(new ArrayBuffer(8)); // 64 bits

    public constructor() {
        this.sink = new ArrayBufferSink();
        this.sink.start({
            asUint8Array: true,
            stream: true,
        });
    }

    public getBuffer(): Buffer {
        return Buffer.from(this.sink.flush() as Uint8Array);
    }

    public writeUnt8(val: number) {
        this.view.setUint8(0, val);
        this.sink.write(this.view.buffer.slice(0, 1));
    }

    public writeInt8(val: number) {
        this.view.setInt8(0, val);
        this.sink.write(this.view.buffer.slice(0, 1));
    }

    public writeUnt16(val: number) {
        this.view.setUint16(0, val);
        this.sink.write(this.view.buffer.slice(0, 2));
    }

    public writeInt16(val: number) {
        this.view.setInt16(0, val);
        this.sink.write(this.view.buffer.slice(0, 2));
    }

    public writeUnt32(val: number) {
        this.view.setUint32(0, val);
        this.sink.write(this.view.buffer.slice(0, 4));
    }

    public writeInt32(val: number) {
        this.view.setInt32(0, val);
        this.sink.write(this.view.buffer.slice(0, 4));
    }

    public writeUnt64(val: number | bigint) {
        if (typeof val === "number") {
            val = BigInt(val);
        }
        this.view.setBigUint64(0, val);
        this.sink.write(this.view.buffer);
    }

    public writeInt64(val: number | bigint) {
        if (typeof val === "number") {
            val = BigInt(val);
        }
        this.view.setBigInt64(0, val);
        this.sink.write(this.view.buffer);
    }

    /** length-prefixed (u32), UTF-8 encoded */
    readonly #stringEncoder = new TextEncoder();
    public writeString(str: string) {
        const bytes = this.#stringEncoder.encode(str);
        this.writeUnt32(bytes.byteLength);
        this.sink.write(bytes);
    }

    /** length-prefixed (u32), UTF-8 encoded */
    public writeBufWithLen(buf: Buffer) {
        this.writeUnt32(buf.byteLength);
        this.writeBufRaw(buf);
    }

    public writeBufRaw(buf: Buffer) {
        this.sink.write(buf);
    }
}

/** Each read advances the internal offset into the buffer. */
export class BufferReader {
    private offset = 0;

    public constructor(private readonly buffer: Buffer) {}

    public readUnt8(): number {
        const val = this.buffer.readUInt8(this.offset);
        this.offset += 1;
        return val;
    }

    public readInt8(): number {
        const val = this.buffer.readInt8(this.offset);
        this.offset += 1;
        return val;
    }

    public readUnt16(): number {
        const val = this.buffer.readUInt16BE(this.offset);
        this.offset += 2;
        return val;
    }

    public readInt16(): number {
        const val = this.buffer.readInt16BE(this.offset);
        this.offset += 2;
        return val;
    }

    public readUnt32(): number {
        const val = this.buffer.readUInt32BE(this.offset);
        this.offset += 4;
        return val;
    }

    public readInt32(): number {
        const val = this.buffer.readInt32BE(this.offset);
        this.offset += 4;
        return val;
    }

    public readUnt64(): number {
        const val = this.buffer.readBigUInt64BE(this.offset);
        if (val > Number.MAX_SAFE_INTEGER) {
            throw new Error(`64-bit number too big: ${val}`);
        }
        this.offset += 8;
        return Number(val);
    }

    public readInt64(): number {
        const val = this.buffer.readBigInt64BE(this.offset);
        if (val > Number.MAX_SAFE_INTEGER) {
            throw new Error(`64-bit number too big: ${val}`);
        }
        if (val < Number.MIN_SAFE_INTEGER) {
            throw new Error(`64-bit number too small: ${val}`);
        }
        this.offset += 8;
        return Number(val);
    }

    readonly #stringDecoder = new TextDecoder("utf-8");
    /** length-prefixed (u32), UTF-8 encoded */
    public readString(): string {
        return this.#stringDecoder.decode(this.readBufWithLen());
    }

    public readBufWithLen(): Buffer {
        return this.readBufLen(this.readUnt32());
    }

    public readBufLen(length: number): Buffer {
        // simply returning a slice() would retain the entire buf in memory
        const buffer = Buffer.allocUnsafe(length);
        this.buffer.copy(buffer, 0, this.offset, this.offset + length);
        this.offset += length;
        return buffer;
    }

    /** any reads after this will fail */
    public readRemainder(): Buffer {
        return this.readBufLen(this.buffer.length - this.offset);
    }
}
