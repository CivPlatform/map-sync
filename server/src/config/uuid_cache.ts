import * as z from "zod";
import { parseConfigFile, saveConfigFile } from "./mod";

export const UUID_CACHE_FILENAME = "uuid_cache.json";
export const UUID_CACHE_SCHEMA = z.record(z.string().uuid());
export const entries = new Map<string, string>();

/** Saves the UUID cache to uuid_cache.json */
export function save() {
    saveConfigFile(
        UUID_CACHE_FILENAME,
        Object.fromEntries(entries.entries())
    );
}

export function load() {
    entries.clear();
    const configured: Record<string, string> = parseConfigFile(
        UUID_CACHE_FILENAME,
        UUID_CACHE_SCHEMA.parse,
        () => ({})
    );
    for (const [key, value] of Object.entries(configured)) {
        entries.set(key, value);
    }
}
