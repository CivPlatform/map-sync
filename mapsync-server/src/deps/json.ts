export type JSONObject = { [key: string]: JSONValue | undefined };
export type JSONArray = JSONValue[];
export type JSONValue =
    | JSONObject
    | JSONArray
    | string
    | number
    | boolean
    | null;

/**
 * Wrapper function for JSON.parse() that provides a proper return type.
 */
export function parse(raw: string): JSONValue {
    return JSON.parse(raw);
}
