import lib_fs from "fs";
import { loadOrSaveDefaultStringFile } from "./utilities";
import { getErrorType, ErrorType } from "./deps/errors";
import { parseConfigFile, saveConfigFile } from "./config/mod";
import * as z from "zod";

export const DATA_FOLDER = process.env["MAPSYNC_DATA_DIR"] ?? "./mapsync";
try {
    lib_fs.mkdirSync(DATA_FOLDER, { recursive: true });
    console.log(`Created data folder "${DATA_FOLDER}"`);
} catch (e: any) {
    if (getErrorType(e) !== ErrorType.FileExists) throw e;
    console.log(`Using data folder "${DATA_FOLDER}"`);
}

// Force initialize
export type Config = z.infer<typeof ConfigSchema>;
export const ConfigSchema = z.object({
    whitelist: z.boolean().default(true),
});

let config: Config | null = null;
export function getConfig(): Config {
    if (config === null) {
        config = parseConfigFile(
            "config.json",
            ConfigSchema.parse,
            () => ({
                whitelist: true
            })
        );
    }
    return config;
}
getConfig();

// ------------------------------------------------------------ //
// Whitelist
// ------------------------------------------------------------ //

export const WHITELIST_FILENAME = "whitelist.json";
export const WHITELIST_SCHEMA = z.array(z.string().uuid());
export const whitelist = new Set<string>();

/** Loads the whitelist from whitelist.json */
export function whitelist_load() {
    whitelist.clear();
    const entries: string[] = parseConfigFile(
        WHITELIST_FILENAME,
        WHITELIST_SCHEMA.parse,
        () => []
    );
    for (const entry of entries) {
        whitelist.add(entry);
    }
}
whitelist_load();

/** Saves the whitelist to whitelist.json */
export function whitelist_save() {
    saveConfigFile(WHITELIST_FILENAME, Array.from(whitelist.values()));
}

// ------------------------------------------------------------ //
// UUID Cache
// ------------------------------------------------------------ //

export const UUID_CACHE_FILENAME = "uuid_cache.json";
export const UUID_CACHE_SCHEMA = z.record(z.string().uuid());
export const uuid_cache = new Map<string, string>();

/** Saves the UUID cache to uuid_cache.json */
export function uuid_cache_save() {
    saveConfigFile(UUID_CACHE_FILENAME, Object.fromEntries(uuid_cache.entries()));
}

export function uuid_cache_load() {
    uuid_cache.clear();
    const entries: Record<string, string> = parseConfigFile(
        UUID_CACHE_FILENAME,
        UUID_CACHE_SCHEMA.parse,
        () => ({})
    );
    for (const [key, value] of Object.entries(entries)) {
        uuid_cache.set(key, value);
    }
}
uuid_cache_load();
