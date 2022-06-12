import lib_fs from "fs";
import { Mutex } from "async-mutex";
import fetch from 'node-fetch'
import { doesExist, loadOrSaveDefaultStringFile } from "./utilities";
import { z, ZodError, ZodIssue } from "zod";
import * as util from "util";

export const ENOENT = -2;
export const EEXIST = -17;
export enum OsError {
	FileExists,
	FileNotFound,
	UNKNOWN
}
export function get_os_error(e: any): OsError {
	if (typeof e !== "object") return OsError.UNKNOWN;
	if (e.errno === ENOENT) return OsError.FileNotFound;
	if (e.errno === EEXIST) return OsError.FileExists;
	return OsError.UNKNOWN;
}

export const DATA_FOLDER = process.env["MAPSYNC_DATA_DIR"] ?? "./mapsync";
try {
	lib_fs.mkdirSync(DATA_FOLDER, { recursive: true });
	console.log(`Created data folder "${DATA_FOLDER}"`);
} catch(e: any) {
	if (get_os_error(e) !== OsError.FileExists) throw e;
	console.log(`Using data folder "${DATA_FOLDER}"`);
}

export const CONFIG_FILE = `${DATA_FOLDER}/config.json`;
export const WHITELIST_FILE = `${DATA_FOLDER}/whitelist.json`;
export const UUID_CACHE_FILE = `${DATA_FOLDER}/uuid_cache.json`;

/** The config.json file */
export interface Config {
	/** Whether the whitelist is being enforced */
	whitelist: boolean;
}

const default_config: Config = {
	//Enable whitelist by default to prevent *tomfoolery*
	whitelist: true,
};

// Force initialize
const CONFIG_MUTEX = new Mutex();
let config: Config | null = null;
export async function getConfig(): Promise<Config> {
	return await CONFIG_MUTEX.runExclusive(async () => {
		if (config === null) {
			const json: any = JSON.parse(await loadOrSaveDefaultStringFile(CONFIG_FILE, JSON.stringify(default_config)));
			if (typeof json !== "object") {
				throw new Error("Config file wasn't an JSON object");
			}
			config = json as Config;
		}
		return config;
	})
	.catch((e) => {
		console.error("[Config] An error occurred when attempting to read `config.json`");
		console.error(e);
		throw e;
	});
}

getConfig();

// ------------------------------------------------------------ //
// Whitelist
// ------------------------------------------------------------ //

// A "queue" of sorts so operations don't conflict
const WHITELIST_MUTEX = new Mutex();
export const whitelist = new Set<string>();

/** Loads the whitelist from whitelist.json */
export async function whitelist_load() {

	// get url from env vars
	const url = process.env["WHITELIST_URL"]

	// check if it was provided to the server
	if (url !== undefined) {
		const res = await fetch(url)
		const json = await res.json()

		whitelist_verify(json);

		whitelist.clear();
		for (const entry of json as string[]) {
			whitelist.add(entry);
		}
		console.log("[Whitelist] Loaded whitelist from url");
	} else {
		
		WHITELIST_MUTEX
		.runExclusive(async () => {
			const json: any = JSON.parse(await loadOrSaveDefaultStringFile(WHITELIST_FILE, "[]"));
			
			whitelist_verify(json);

			whitelist.clear();
			for (const entry of json as string[]) {
				whitelist.add(entry);
			}
			console.log("[Whitelist] Loaded whitelist from file");
		})
		.catch((e) => {
			if (get_os_error(e) === OsError.FileNotFound) {
				console.error("[Whitelist] No whitelist file was found. An empty one will be created shortly.");
				whitelist_save(); // Don't await, will cause deadlock
			}
			else {
				console.error("[Whitelist] Error occurred while loading the whitelist from disk");
				console.error(e);
				console.error("[Whitelist] The whitelist will be loaded as empty");
			}
		});
	}

	
}

/**
 * Checks that the provided data is actually in the proper json format
 * @param json 
 */
export function whitelist_verify(json: any) {
	if (!Array.isArray(json)) {
		throw new Error("Whitelist file wasn't an array");
	}
	for (const entry of json as any[]) {
		if (typeof entry !== "string") {
			throw new Error(`Entry "${entry}" is not a string`);
		}
	}
}

/** Saves the whitelist to whitelist.json */
export async function whitelist_save() {
	WHITELIST_MUTEX
		.runExclusive(async () => {
			await lib_fs.promises.writeFile(WHITELIST_FILE, JSON.stringify(Array.from(whitelist)));
			console.log("[Whitelist] Saved whitelist");
		})
		.catch((e) => {
			console.error("[Whitelist] Error occurred while saving the whitelist to the disk");
			console.error(e);
		});
}

// Load whitelist on startup
whitelist_load();

/** Checks if the given uuid is in the whitelist */
export function whitelist_check(uuid: string): boolean {
	return whitelist.has(uuid);
}

// These need to enqueue in whitelist_operation in case they are called in the middle of a load operation
/** Adds the given uuid to the whitelist */
export async function whitelist_add(uuid: string) {
	WHITELIST_MUTEX
		.runExclusive(async () => {
			whitelist.add(uuid);
			console.log(`[Whitelist] Added user "${uuid}" to the whitelist`);
		})
		.catch((e) => {
			console.error(`[Whitelist] Error occurred adding user "${uuid}" to the whitelist`);
			console.error(e);
		});
}

/** Removes the given uuid from the whitelist */
export async function whitelist_remove(uuid: string) {
	WHITELIST_MUTEX
		.runExclusive(async () => {
			whitelist.delete(uuid);
			console.log(`[Whitelist] Removed user "${uuid}" from the whitelist`);
		})
		.catch((e) => {
			console.error(`[Whitelist] Error occurred removing user "${uuid}" to the whitelist`);
			console.error(e);
		});
}

// ------------------------------------------------------------ //
// UUID Cache
// ------------------------------------------------------------ //

const UUID_CACHE_MUTEX = new Mutex();
export const uuid_cache = new Map<string, string>();
export const UuidCacheZod = z.record(z.string());

/**
 * Saves the UUID cache to uuid_cache.json
 */
export async function uuid_cache_save() {
	UUID_CACHE_MUTEX
		.runExclusive(async () => {
			await lib_fs.promises.writeFile(UUID_CACHE_FILE, JSON.stringify(Object.fromEntries(uuid_cache)));
			console.log("[UUID Cache] Saved UUID cache");
		})
		.catch((e) => {
			console.error("[UUID Cache] Error occurred while saving the whitelist to the disk");
			console.error(e);
		});
}

/**
 * Loads the UUID cache from uuid_cache.json, replacing the current cache (if any) entirely.
 */
export async function uuid_cache_load() {
	UUID_CACHE_MUTEX
		.runExclusive(async () => {
			let json: Record<string, string> = null!;
			try {
				json = UuidCacheZod.parse(JSON.parse(await loadOrSaveDefaultStringFile(UUID_CACHE_FILE, "{}")));
			}
			catch (e) {
				if (e instanceof ZodError) {
					throw new Error("UUID Cache error: " + util.inspect(e.issues.map((issue: ZodIssue) =>
						"• [" + (issue.path.length < 1 ? "." : issue.path.join(".")) + "]: " + issue.message
					), { compact: false }));
				}
				throw e;
			}
			uuid_cache.clear();
			for (const [key, value] of Object.entries(json)) {
				uuid_cache.set(key, value);
			}
			console.log("[UUID Cache] Saved UUID cache");
		})
		.catch((e) => {
			console.error("[UUID Cache] An error occurred when attempting to read `uuid_cache.json`");
			console.error(e);
			console.error("[UUID Cache] A new uuid cache will be created");
		});
}

// Load the UUID cache on startup
uuid_cache_load();

/**
 * Associates an in-game name with a UUID.
 *
 * @param name The name to associate.
 * @param uuid The UUID to associate the name with.
 */
export async function uuid_cache_store(name: string, uuid: string) {
	if (!doesExist(name)) {
		throw new Error("Name cannot be null!");
	}
	if (!doesExist(uuid)) {
		throw new Error("UUID cannot be null!");
	}
	uuid_cache.set(uuid, name);
	console.log(`[UUID Cache] cached "${name}" as UUID "${uuid}"`);
	await uuid_cache_save();
}

/**
 * Attempts to get the UUID for a given in-game name.
 *
 * @param name The name to search with.
 * @return Returns the matched UUID, or null.
 */
export function uuid_cache_lookup(name: string): string | null {
	for (const [knownUuid, knownName] of uuid_cache.entries()) {
		if (knownName === name) {
			return knownUuid;
		}
	}
	return null;
}

