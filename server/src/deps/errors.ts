import std_os from "os";

export enum ErrorType {
    FileExists,
    FileNotFound,
    UNKNOWN
}

/**
 * Attempts to transform Node's less-than-helpful exceptions into something
 * more readable and logic-able.
 */
export function getErrorType(error: any): ErrorType {
    switch (Math.abs(error.errno ?? Infinity)) {
        case std_os.constants.errno.ENOENT:
            return ErrorType.FileNotFound;
        case std_os.constants.errno.EEXIST:
            return ErrorType.FileExists;
        default:
            return ErrorType.UNKNOWN;
    }
}
