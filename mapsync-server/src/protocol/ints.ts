export abstract class IntType {
    /**
     * @return Returns how many bytes were encoded
     */
    public abstract encodeOnto(
        sink: Buffer,
        offset: number,
        value: number,
    ): number;
    /**
     * @return Returns how many bytes were encoded
     */
    public abstract encodeAs(
        value: number,
    ): Buffer;
    /**
     * @return Returns how many bytes were decoded, and the decoded value
     */
    public abstract decode(source: Buffer, offset: number): [number, number];
}

export const U8 = new class U8 extends IntType {
    public encodeOnto(sink: Buffer, offset: number, value: number): number {
        if (value < 0 || value >= 0xFF) {
            throw new Error(`illegal U8 value! [${value}]`);
        }
        sink.writeUInt8(value, offset);
        return 1;
    }
    public encodeAs(value: number): Buffer {
        const bytes = Buffer.allocUnsafe(1);
        this.encodeOnto(bytes, 0, value);
        return bytes;
    }
    public decode(source: Buffer, offset: number): [number, number] {
        return [1, source.readUInt8(offset)];
    }
};

export const U16 = new class U16 extends IntType {
    public encodeOnto(sink: Buffer, offset: number, value: number): number {
        if (value < 0 || value >= 0xFF_FF) {
            throw new Error(`illegal U16 value! [${value}]`);
        }
        sink.writeUInt16BE(value, offset);
        return 2;
    }
    public encodeAs(value: number): Buffer {
        const bytes = Buffer.allocUnsafe(2);
        this.encodeOnto(bytes, 0, value);
        return bytes;
    }
    public decode(source: Buffer, offset: number): [number, number] {
        return [2, source.readUInt16BE(offset)];
    }
};
