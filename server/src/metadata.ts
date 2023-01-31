import lib_fs from "fs";
import { Mutex } from "async-mutex";
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

export const UUID_CACHE_FILE = `${DATA_FOLDER}/uuid_cache.json`;


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

const UUID_CACHE_MUTEX = new Mutex();
/** A cache storing uuids by player IGN */
export const uuid_cache = new Map<string, string>();

/** Saves the UUID cache to uuid_cache.json */
export async function uuid_cache_save() {
    UUID_CACHE_MUTEX.runExclusive(async () => {
        await lib_fs.promises.writeFile(
            UUID_CACHE_FILE,
            JSON.stringify(Array.from(uuid_cache)),
        );
        console.log("[UUID Cache] Saved UUID cache");
    }).catch((e) => {
        console.error(
            "[UUID Cache] Error occured while saving the whitelist to the disk",
        );
        console.error(e);
    });
}

export async function uuid_cache_load(): Promise<void> {
    UUID_CACHE_MUTEX.runExclusive(async () => {
        const json: any = JSON.parse(
            await loadOrSaveDefaultStringFile(UUID_CACHE_FILE, "{}"),
        );
        if (typeof json !== "object") {
            throw new Error("UUID cache file wasn't an JSON object");
        }
        uuid_cache.clear();
        for (const [key, value] of Object.entries(json)) {
            uuid_cache.set(key, String(value));
        }
        console.log("[UUID Cache] Saved UUID cache");
    }).catch((e) => {
        if (getErrorType(e) === ErrorType.FileNotFound) {
            console.error(
                "[UUID Cache] No uuid cache file was found. A new one will be created shortly.",
            );
            uuid_cache_save(); // Don't await, will cause deadlock
        } else {
            console.error(
                "[UUID Cache] An error occured when attempting to read `uuid_cache.json`",
            );
            console.error(e);
            console.error("[UUID Cache] A new uuid cache will be created");
        }
    });
}

// Load UUID cache on startup
uuid_cache_load();

/** Caches a player IGN with their UUID */
export function uuid_cache_store(ign: string, uuid: string) {
    if (uuid == null || ign == null) return;
    uuid_cache.set(ign, uuid);
    console.log(`[UUID Cache] cached "${ign}" as UUID "${uuid}"`);
    uuid_cache_save();
}

/** Looks up a UUID from an IGN */
export function uuid_cache_lookup(ign: string): string | null {
    return uuid_cache.get(ign) ?? null;
}
