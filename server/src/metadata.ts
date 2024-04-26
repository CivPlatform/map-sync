import node_fs from "node:fs";
import node_path from "node:path";
import { Mutex } from "async-mutex";
import * as errors from "./deps/errors";
import * as json from "./deps/json";
import * as z from "zod";
import { fromZodError } from "zod-validation-error";

export const DATA_FOLDER = process.env["MAPSYNC_DATA_DIR"] ?? "./mapsync";
try {
    node_fs.mkdirSync(DATA_FOLDER, { recursive: true });
    console.log(`Created data folder "${DATA_FOLDER}"`);
} catch (e: any) {
    if (errors.getErrorType(e) !== errors.ErrorType.FileExists) throw e;
    console.log(`Using data folder "${DATA_FOLDER}"`);
}

/**
 * Attempts to read a config file within the DATA_FOLDER. If the file isn't
 * found then a new file is created with default contents.
 *
 * @param file The file-name, eg: "config.json"
 * @param parser Use this transform and check the raw JSON parsed from the file. (Put your Zod.parse here)
 * @param defaultSupplier A function that returns a fully-valid default config for this file.
 */
function parseConfigFile<T>(
    file: string,
    parser: (raw: json.JSONValue) => T,
    defaultSupplier: () => any,
): T {
    file = node_path.resolve(DATA_FOLDER, file);
    let fileContents: string = null!;
    try {
        fileContents = node_fs.readFileSync(file, "utf8");
    } catch (e) {
        if (errors.getErrorType(e) !== errors.ErrorType.FileNotFound) {
            throw e;
        }
        // Could not find the config file, so attempt to create a default one
        const defaultContent = defaultSupplier();
        node_fs.writeFileSync(
            file,
            JSON.stringify(defaultContent, null, 2),
            "utf8",
        );
        return defaultContent;
    }
    try {
        return parser(json.parse(fileContents));
    } catch (e) {
        if (e instanceof z.ZodError) {
            throw "Could not parse " + file + ": " + fromZodError(e);
        }
        throw e;
    }
}

/**
 * Convenience function to quickly save a config's contents.
 *
 * @param file The file-name, eg: "config.json"
 * @param content The file's contents, which will be JSON-stringified if it's not already a string.
 */
function saveConfigFile(file: string, content: any) {
    file = node_path.resolve(DATA_FOLDER, file);
    if (typeof content !== "string") {
        content = JSON.stringify(content, null, 2);
    }
    node_fs.writeFileSync(file, content, "utf8");
}

// ------------------------------------------------------------ //
// Config
// ------------------------------------------------------------ //

const CONFIG_FILE = "config.json";
const CONFIG_SCHEMA = z.object({
    host: z.string().default("0.0.0.0"),
    port: z.coerce.number().positive().max(65535).default(12312),
    gameAddress: z.string(),
    whitelist: z.boolean().default(true),
});
export type Config = z.infer<typeof CONFIG_SCHEMA>;
export function getConfig(): Config {
    return parseConfigFile(CONFIG_FILE, CONFIG_SCHEMA.parse, () => ({
        gameAddress: "localhost:25565",
        whitelist: true,
    }));
}

// ------------------------------------------------------------ //
// Whitelist
// ------------------------------------------------------------ //

const WHITELIST_FILE = "whitelist.json";
const WHITELIST_MUTEX = new Mutex();
const WHITELIST_SCHEMA = z.array(z.string().uuid());
export const whitelist = new Set<string>();

export async function loadWhitelist() {
    await WHITELIST_MUTEX.runExclusive(async () => {
        const parsed = parseConfigFile(
            WHITELIST_FILE,
            WHITELIST_SCHEMA.parse,
            () => [],
        );
        whitelist.clear();
        for (const entry of parsed) {
            whitelist.add(entry);
        }
        console.log("[Whitelist] Loaded whitelist");
    });
}

export async function saveWhitelist() {
    await WHITELIST_MUTEX.runExclusive(async () => {
        saveConfigFile(WHITELIST_FILE, JSON.stringify(Array.from(whitelist)));
        console.log("[Whitelist] Saved whitelist");
    });
}

// ------------------------------------------------------------ //
// UUID Cache
// ------------------------------------------------------------ //

const UUID_CACHE_FILE = "uuid_cache.json";
const UUID_CACHE_MUTEX = new Mutex();
const UUID_CACHE_SCHEMA = z.record(z.string().uuid());
//                         IGN     UUID
const uuid_cache = new Map<string, string>();

export function getCachedPlayerUuid(playerName: string) {
    return uuid_cache.get(playerName) ?? null;
}

export function cachePlayerUuid(playerName: string, playerUUID: string) {
    uuid_cache.set(playerName, playerUUID);
}

export async function loadUuidCache() {
    await UUID_CACHE_MUTEX.runExclusive(async () => {
        const parsed = parseConfigFile(
            UUID_CACHE_FILE,
            UUID_CACHE_SCHEMA.parse,
            () => ({}),
        );
        uuid_cache.clear();
        for (const [key, value] of Object.entries(parsed)) {
            uuid_cache.set(key, String(value));
        }
        console.log("[UUID Cache] Loaded UUID cache");
    });
}

export async function saveUuidCache() {
    await UUID_CACHE_MUTEX.runExclusive(async () => {
        saveConfigFile(
            UUID_CACHE_FILE,
            JSON.stringify(Object.fromEntries(uuid_cache.entries())),
        );
        console.log("[UUID Cache] Saved UUID cache");
    });
}
