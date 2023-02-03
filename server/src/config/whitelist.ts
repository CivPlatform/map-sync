import * as z from "zod";
import { parseConfigFile, saveConfigFile } from "./mod";

export const WHITELIST_FILENAME = "whitelist.json";
export const WHITELIST_SCHEMA = z.array(z.string().uuid());
export const entries = new Set<string>();

/** Loads the whitelist from whitelist.json */
export function load() {
    entries.clear();
    const configured: string[] = parseConfigFile(
        WHITELIST_FILENAME,
        WHITELIST_SCHEMA.parse,
        () => []
    );
    for (const entry of configured) {
        entries.add(entry);
    }
}

/** Saves the whitelist to whitelist.json */
export function save() {
    saveConfigFile(WHITELIST_FILENAME, Array.from(entries.values()));
}
