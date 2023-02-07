import lib_path from "node:path";
import lib_fs from "node:fs";
import * as errors from "../deps/errors";
import type { JSONValue } from "../deps/json";

export const DATA_FOLDER = process.env["MAPSYNC_DATA_DIR"] ?? "./mapsync";

/**
 * Attempts to read a config file within the DATA_FOLDER. If the file isn't
 * found then a new file is created with default contents.
 *
 * @param file The file-name, eg: "config.json"
 * @param parser Use this transform and check the raw JSON parsed from the file. (Put your Zod.parse here)
 * @param defaultSupplier A function that returns a fully-valid default config for this file.
 */
export function parseConfigFile<T>(
    file: string,
    parser: (raw: JSONValue) => T,
    defaultSupplier: () => T
): T {
    file = lib_path.join(DATA_FOLDER, file);
    let fileContents: string = null!;
    try {
        fileContents = lib_fs.readFileSync(file, "utf8");
    } catch (e) {
        if (errors.getErrorType(e) !== errors.ErrorType.FileNotFound) {
            throw e;
        }
        // Could not find the config file, so attempt to create a default one
        const defaultContent = defaultSupplier();
        lib_fs.writeFileSync(file, JSON.stringify(defaultContent), "utf8");
        return defaultContent;
    }
    return parser(JSON.parse(fileContents) as JSONValue);
}

/**
 * Convenience function to quickly save a config's contents.
 *
 * @param file The file-name, eg: "config.json"
 * @param content The file's contents, which will be JSON-stringified if it's not already a string.
 */
export function saveConfigFile(file: string, content: any) {
    file = lib_path.join(DATA_FOLDER, file);
    if (typeof content !== "string") {
        content = JSON.stringify(content);
    }
    lib_fs.writeFileSync(file, content, "utf8");
}
