import {
    asInt16,
    asInt32,
    asInt64,
    asUnt10,
    asUnt16,
    asUnt31,
    asUnt5,
    asUnt8,
    int16,
    int32,
    int64,
    numeric,
    unt10,
    unt16,
    unt31,
    unt5,
    unt8,
} from "../deps/ints";

/** Each read advances the internal offset into the buffer. */
export class BufferReader {
    private offset = 0;

    public constructor(private readonly buffer: Buffer) {}

    public remainder(): unt31 {
        return asUnt31(this.buffer.length - this.offset);
    }

    public readUnt5(): unt5 {
        const value = this.buffer.readUInt8(this.offset);
        this.offset += 1;
        return asUnt5(value);
    }

    public readUnt8(): unt8 {
        const value = this.buffer.readUInt8(this.offset);
        this.offset += 1;
        return asUnt8(value);
    }

    public readUnt10(): unt10 {
        const value = this.buffer.readUInt16BE(this.offset);
        this.offset += 2;
        return asUnt10(value);
    }

    public readUnt16(): unt16 {
        const value = this.buffer.readUInt16BE(this.offset);
        this.offset += 2;
        return asUnt16(value);
    }

    public readInt16(): int16 {
        const value = this.buffer.readInt16BE(this.offset);
        this.offset += 2;
        return asInt16(value);
    }

    public readUnt31(): unt31 {
        const value = this.buffer.readInt32BE(this.offset);
        this.offset += 4;
        return asUnt31(value);
    }

    public readInt32(): int32 {
        const value = this.buffer.readInt32BE(this.offset);
        this.offset += 4;
        return asInt32(value);
    }

    public readInt64(): int64 {
        const value = this.buffer.readBigInt64BE(this.offset);
        this.offset += 8;
        return asInt64(value);
    }

    public readBytesOfLength(length: numeric): Buffer {
        length = asUnt31(length);
        length = Number(length);
        const bytes = Buffer.allocUnsafe(length);
        this.buffer.copy(bytes, 0, this.offset, this.offset + length);
        this.offset += length;
        return bytes;
    }

    public readString(): string {
        return this.readBytesOfLength(this.readUnt8()).toString("utf8");
    }

    /** any reads after this will fail */
    public readRemainder(): Buffer {
        return this.readBytesOfLength(this.remainder());
    }
}

type LengthPrefixSetter = (this: BufferWriter, length: numeric) => void;

/** Each write advances the internal offset into the buffer.
 * Grows the buffer to twice the current size if a write would exceed the buffer. */
export class BufferWriter {
    private offset = 0;
    private buffer: Buffer;

    public constructor(initialSize: unt31 = asUnt31(1024)) {
        this.buffer = Buffer.alloc(Number(initialSize));
    }

    /** Returns a slice reference to the written bytes so far. */
    public getBuffer(): Buffer {
        return this.buffer.subarray(0, this.offset);
    }

    public writeUnt5(value: numeric) {
        value = asUnt5(value);
        this.writeUnt8(value as unknown as unt8);
    }

    public writeUnt8(value: numeric) {
        value = asUnt8(value);
        this.ensureSpace(1);
        this.buffer.writeUInt8(Number(value), this.offset);
        this.offset += 1;
    }

    public writeUnt10(value: numeric) {
        value = asUnt10(value);
        this.writeUnt16(value as unknown as unt16);
    }

    public writeUnt16(value: numeric) {
        value = asUnt16(value);
        this.ensureSpace(2);
        this.buffer.writeUInt16BE(Number(value), this.offset);
        this.offset += 2;
    }

    public writeInt16(value: numeric) {
        value = asInt16(value);
        this.ensureSpace(2);
        this.buffer.writeInt16BE(Number(value), this.offset);
        this.offset += 2;
    }

    public writeUnt31(value: numeric) {
        value = asUnt31(value);
        this.writeInt32(value as unknown as int32);
    }

    public writeInt32(value: numeric) {
        value = asInt32(value);
        this.ensureSpace(4);
        this.buffer.writeInt32BE(Number(value), this.offset);
        this.offset += 4;
    }

    public writeInt64(value: numeric) {
        value = asInt64(value);
        this.ensureSpace(8);
        this.buffer.writeBigInt64BE(value, this.offset);
        this.offset += 8;
    }

    public writeBytes(buf: Buffer) {
        this.ensureSpace(buf.length);
        this.buffer.set(buf, this.offset);
        this.offset += buf.length;
    }

    public writeLengthPrefixedBytes(
        lengthSetter: LengthPrefixSetter,
        data: Buffer,
    ) {
        lengthSetter.bind(this)(data.length);
        this.buffer.set(data, this.offset);
        this.offset += data.length;
    }

    public writeString(value: string) {
        const bytes: Buffer = Buffer.from(value, "utf8");
        this.writeUnt8(bytes.length);
        this.writeBytes(bytes);
    }

    private ensureSpace(bytes: number) {
        let length = this.buffer.length;
        while (length <= this.offset + bytes) {
            length *= 2;
        }
        if (length > this.buffer.length) {
            const replacement = Buffer.allocUnsafe(length);
            this.buffer.copy(replacement, 0, 0, this.offset);
            this.buffer = replacement;
        }
    }
}
