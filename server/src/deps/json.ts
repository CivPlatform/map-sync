export type JSONObject = { [key: string]: JSONValue | undefined };
export type JSONArray = JSONValue[];
export type JSONValue = JSONObject | JSONArray | string | number | boolean | null;
