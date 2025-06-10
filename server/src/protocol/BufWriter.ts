import { ArrayBufferSink } from "bun";

export class BufWriter {
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
