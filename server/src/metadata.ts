import lib_fs from "fs";

export const CONFIG_FILE = "config.json";
export const WHITELIST_FILE = "whitelist.json";
export const UUID_CACHE_FILE = "uuid_cache.json";

/** The config.json file */
export interface Config {
	/** Whether the whitelist is being enforced */
	whitelist: boolean;
}

const default_config: Config = {
	//Enable whitelist by default to prevent *tomfoolery*
	whitelist: true,
};

//TODO: add config reloading?
export let config: Config;
try {
	config = JSON.parse(lib_fs.readFileSync("config.json").toString());
	if (typeof config !== "object") throw new Error("Config file is not an object");
	if (typeof config.whitelist !== "boolean") throw new Error("config.whitelist isn't a boolean");
} catch (e) {
	console.error("[Config] An error occured when attempting to read `config.json`");
	console.error(e);
	console.error("[Config] The default configuration file will be used");

	config = default_config;
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
	})
	.catch(e => {
		console.error("[Whitelist] Error occured while loading the whitelist from disk");
		console.error(e);
		console.error("[Whitelist] The whitelist will be loaded as empty");
	});
	return whitelist_operations;
}
/** Saves the whitelist to whitelist.json */
export function whitelist_save(): Promise<void> {
	whitelist_operations = whitelist_operations.then(async () => {
		await lib_fs.promises.writeFile(WHITELIST_FILE, JSON.stringify(Array.from(whitelist)));
	})
	.catch(e => {
		console.error("[Whitelist] Error occured while saving the whitelist to the disk");
		console.error(e);
	});
	return whitelist_operations;
}

/** Checks if the given uuid is in the whitelist */
export function whitelist_check(uuid: string): boolean { return whitelist.has(uuid); }

//These need to enqueue in whitelist_operation in case they are called in the middle of a load operation
/** Adds the given uuid to the whitelist */
export function whitelist_add(uuid: string): Promise<void> {
	if (uuid == null) return Promise.resolve();

	//Use a partial operation here to propogate errors to the caller
	const partial_operation = whitelist_operations.then(async () => {
		whitelist.add(uuid);
	});
	whitelist_operations = partial_operation.catch(e => {
		console.error(`[Whitelist] Error occured adding user "{uuid}" to the whitelist`);
		console.error(e);
	});
	return partial_operation;
}
/** Removes the given uuid from the whitelist */
export function whitelist_remove(uuid: string): Promise<void> {
	//Use a partial operation here to propogate errors to the caller
	const partial_operation = whitelist_operations.then(async () => {
		whitelist.delete(uuid);
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
try {
	const cache_file = JSON.parse(lib_fs.readFileSync(UUID_CACHE_FILE).toString());
	if (!Array.isArray(cache_file)) throw new Error("Cache file wasn't an array");
	uuid_cache = new Map(cache_file);
} catch (e) {
	console.error("[UUID Cache] An error occured when attempting to read `uuid_cache.json`");
	console.error(e);
	console.error("[UUID Cache] A new uuid cache will be created");
}

/** Caches a player IGN with their UUID */
export function cache_uuid(ign: string, uuid: string) {
	if (uuid == null || ign == null) return;
	uuid_cache.set(ign, uuid);	
	uuid_cache_save();
}
/** Looks up a UUID from an IGN */
export function lookup_uuid(ign: string): string | null {
	return uuid_cache.get(ign) ?? null;
}

//A save operation pending execution. If one is pending, then no need to queue more.
let save_operation: Promise<void> | null = null;
/** Saves the UUID cache to uuid_cache.json */
export function uuid_cache_save(): Promise<void> {
	if (save_operation != null) return save_operation;
	save_operation = uuid_cache_operations.then(async () => {
		await lib_fs.promises.writeFile(UUID_CACHE_FILE, JSON.stringify(Array.from(uuid_cache)));		
	})
	.catch(e => {
		console.error("[UUID Cache] Error occured while saving the whitelist to the disk");
		console.error(e);
	});
	uuid_cache_operations = save_operation;
	return save_operation;
}
