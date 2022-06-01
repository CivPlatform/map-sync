import lib_fs from "fs";
import lib_path from "path";

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

//Force initialize
export let config: Config = null as unknown as Config;
try {
	config = JSON.parse(lib_fs.readFileSync(CONFIG_FILE).toString());
	if (typeof config !== "object") throw new Error("Config file is not an object");
	if (typeof config.whitelist !== "boolean") throw new Error("config.whitelist isn't a boolean");
} catch (e) {
	if (get_os_error(e) === OsError.FileNotFound) {
		console.error("[Config] Config file not found. Creating with default config...");
		try {
			config = default_config;
			lib_fs.writeFileSync(CONFIG_FILE, JSON.stringify(config));
		} catch (e) {
			console.error("[Config] An error occured when attempting to write `config.json`");
			console.error(e);
			throw e;
		}
	} else {
		console.error("[Config] An error occured when attempting to read `config.json`");
		console.error(e);
		throw e;
	}
}
Object.freeze(config);

//A "queue" of sorts so operations don't conflict
let whitelist_operations: Promise<void> = Promise.resolve();
export let whitelist = new Set<string>();

/** Loads the whitelist from whitelist.json */
export function whitelist_load(): Promise<void> {
	whitelist_operations = whitelist_operations.then(async () => {
		const whitelist_file: string[] = JSON.parse((await lib_fs.promises.readFile(WHITELIST_FILE)).toString());

		if (!Array.isArray(whitelist_file)) throw new Error("Whitelist file wasn't an array");
		whitelist_file.forEach(entry => {
			//TODO possibly verify that each entry is a valid UUID?
			if (typeof entry !== "string") throw new Error(`Entry "{entry}" is not a string`);
		});

		whitelist = new Set(whitelist_file);
		console.log("[Whitelist] Loaded whitelist");
	})
	.catch(e => {
		if (get_os_error(e) === OsError.FileNotFound) {
			console.error("[Whitelist] No whitelist file was found. An empty one will be created shortly.");
			whitelist_save(); //Don't await, will cause deadlock
		} else {
			console.error("[Whitelist] Error occured while loading the whitelist from disk");
			console.error(e);
			console.error("[Whitelist] The whitelist will be loaded as empty");
		}
	});
	return whitelist_operations;
}
/** Saves the whitelist to whitelist.json */
export function whitelist_save(): Promise<void> {
	whitelist_operations = whitelist_operations.then(async () => {
		await lib_fs.promises.writeFile(WHITELIST_FILE, JSON.stringify(Array.from(whitelist)));
		console.log("[Whitelist] Saved whitelist");
	})
	.catch(e => {
		console.error("[Whitelist] Error occured while saving the whitelist to the disk");
		console.error(e);
	});
	return whitelist_operations;
}
//Load whitelist on startup
whitelist_load();

/** Checks if the given uuid is in the whitelist */
export function whitelist_check(uuid: string): boolean { return whitelist.has(uuid); }

//These need to enqueue in whitelist_operation in case they are called in the middle of a load operation
/** Adds the given uuid to the whitelist */
export function whitelist_add(uuid: string): Promise<void> {
	if (uuid == null) return Promise.resolve();

	//Use a partial operation here to propogate errors to the caller
	const partial_operation = whitelist_operations.then(async () => {
		whitelist.add(uuid);
		console.log(`[Whitelist] Added user "${uuid}" to the whitelist`);
	});
	whitelist_operations = partial_operation.catch(e => {
		console.error(`[Whitelist] Error occured adding user "${uuid}" to the whitelist`);
		console.error(e);
	});
	return partial_operation;
}
/** Removes the given uuid from the whitelist */
export function whitelist_remove(uuid: string): Promise<void> {
	//Use a partial operation here to propogate errors to the caller
	const partial_operation = whitelist_operations.then(async () => {
		whitelist.delete(uuid);
		console.log(`[Whitelist] Removed user "${uuid}" from the whitelist`);
	});
	whitelist_operations = partial_operation.catch(e => {
		console.error(`[Whitelist] Error occured removing user "{uuid}" to the whitelist`);
		console.error(e);
	});
	return partial_operation;
}

let uuid_cache_operations: Promise<void> = Promise.resolve();
/** A cache storing uuids by player IGN */
export let uuid_cache = new Map<string, string>();
//A save operation pending execution. If one is pending, then no need to queue more.
let save_operation: Promise<void> | null = null;

/** Saves the UUID cache to uuid_cache.json */
export function uuid_cache_save(): Promise<void> {
	if (save_operation != null) return save_operation;
	save_operation = uuid_cache_operations.then(async () => {
		await lib_fs.promises.writeFile(UUID_CACHE_FILE, JSON.stringify(Array.from(uuid_cache)));		
		console.log("[UUID Cache] Saved UUID cache");
	})
	.catch(e => {
		console.error("[UUID Cache] Error occured while saving the whitelist to the disk");
		console.error(e);
	});
	uuid_cache_operations = save_operation;
	return save_operation;
}

try {
	const cache_file = JSON.parse(lib_fs.readFileSync(UUID_CACHE_FILE).toString());
	if (!Array.isArray(cache_file)) throw new Error("Cache file wasn't an array");
	uuid_cache = new Map(cache_file);
	console.log("[UUID Cache] Loaded UUID cache");
} catch (e) {
	if (get_os_error(e) === OsError.FileNotFound) {
		console.error("[UUID Cache] No uuid cache file was found. A new one will be created shortly.");
		uuid_cache_save(); //Don't await, will cause deadlock
	} else {
		console.error("[UUID Cache] An error occured when attempting to read `uuid_cache.json`");
		console.error(e);
		console.error("[UUID Cache] A new uuid cache will be created");
	}
}

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

