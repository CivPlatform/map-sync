export type numeric = number | bigint;
function ensureBigInt(value: numeric): bigint {
    if (typeof value === "number") {
        if (!Number.isInteger(value)) {
            throw new Error(`invalid integer value: ${value}`);
        }
        value = BigInt(value);
    }
    return value;
}

export type unt5 = bigint & { readonly __brand: "u5" };
export function asUnt5(value: numeric): unt5 {
    value = ensureBigInt(value);
    if (value < 0n || value > 31n) {
        throw new Error(`invalid unt5 value: ${value}`);
    }
    return value as unt5;
}

export type unt8 = bigint & { readonly __brand: "u8" };
export function asUnt8(value: numeric): unt8 {
    value = ensureBigInt(value);
    if (value < 0n || value > 255n) {
        throw new Error(`invalid unt8 value: ${value}`);
    }
    return value as unt8;
}

export type unt10 = bigint & { readonly __brand: "u10" };
export function asUnt10(value: numeric): unt10 {
    value = ensureBigInt(value);
    if (value < 0n || value > 1023n) {
        throw new Error(`invalid unt10 value: ${value}`);
    }
    return value as unt10;
}

export type unt16 = bigint & { readonly __brand: "u16" };
export function asUnt16(value: numeric): unt16 {
    value = ensureBigInt(value);
    if (value < 0n || value > 65535n) {
        throw new Error(`invalid unt16 value: ${value}`);
    }
    return value as unt16;
}

export type int16 = bigint & { readonly __brand: "i16" };
export function asInt16(value: numeric): int16 {
    value = ensureBigInt(value);
    if (value < -32768n || value > 32767n) {
        throw new Error(`invalid int16 value: ${value}`);
    }
    return value as int16;
}

export type unt31 = bigint & { readonly __brand: "u31" };
export function asUnt31(value: numeric): unt31 {
    value = ensureBigInt(value);
    if (value < 0 || value > 2147483647n) {
        throw new Error(`invalid unt31 value: ${value}`);
    }
    return value as unt31;
}

export type int32 = bigint & { readonly __brand: "i32" };
export function asInt32(value: numeric): int32 {
    value = ensureBigInt(value);
    if (value < -2147483648n || value > 2147483647n) {
        throw new Error(`invalid int32 value: ${value}`);
    }
    return value as int32;
}

export type int64 = bigint & { readonly __brand: "i64" };
export function asInt64(value: numeric): int64 {
    value = ensureBigInt(value);
    if (value < -9223372036854775808n || value > 9223372036854775807n) {
        throw new Error(`invalid int64 value: ${value}`);
    }
    return value as int64;
}
