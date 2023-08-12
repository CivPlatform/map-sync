import node_os from 'node:os'
import node_utils from 'node:util'

export enum ErrorType {
	FileExists,
	FileNotFound,
	UNKNOWN,
}

/**
 * Attempts to transform Node's less-than-helpful exceptions into something
 * more readable and logic-able.
 */
export function getErrorType(error: any): ErrorType {
	switch (Math.abs(error.errno ?? Infinity)) {
		case node_os.constants.errno.ENOENT:
			return ErrorType.FileNotFound
		case node_os.constants.errno.EEXIST:
			return ErrorType.FileExists
		default:
			return ErrorType.UNKNOWN
	}
}

/**
 * Utility that guarantees that the error is an instance of Error.
 */
export function ensureError(error: any): Error {
	if (error instanceof Error) {
		return error
	}
	switch (typeof error) {
		case 'string':
			return new Error(error)
		case 'number':
		case 'bigint':
			return new Error(String(error))
	}
	return new Error(node_utils.inspect(error))
}

/**
 * This is useful in cases where you need to throw but can't because of
 * Javascript. Read more for context:
 * https://www.proposals.es/proposals/throw%20expressions
 */
export function inlineThrow<T>(error: any): T {
	throw error
}
