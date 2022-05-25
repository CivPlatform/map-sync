import lib_readline from "readline";

import * as metadata from "./metadata";

const term = lib_readline.createInterface({ input: process.stdin, output: process.stdout });

async function handle_input(input: string): Promise<void> {
	const command_end_i = input.indexOf(" ");
	const command = (command_end_i > -1 ? input.substring(0, command_end_i) : input).toLowerCase();
	const extras = command_end_i > -1 ? input.substring(command_end_i + 1) : "";

	if (command === "ping") console.log("pong");

	else if (command === "whitelist_load") await metadata.whitelist_load();
	else if (command === "whitelist_save") await metadata.whitelist_save();
	else if (command === "whitelist_add") {
		if (extras.length === 0) throw new Error("Did not provide UUID to whitelist");
		const uuid = extras;
		await metadata.whitelist_add(uuid);
	}
	else if (command === "whitelist_add_ign") {
		if (extras.length === 0) throw new Error("Did not provide UUID to whitelist");
		const ign = extras;
		const uuid = metadata.lookup_uuid(ign);
		if (uuid == null) throw new Error("No cached UUID for IGN " + ign);
		await metadata.whitelist_add(uuid);
	}
	else if (command === "whitelist_remove") {
		if (extras.length === 0) throw new Error("Did not provide UUID to whitelist");
		const uuid = extras;
		await metadata.whitelist_remove(uuid);
	}
	else if (command === "whitelist_remove_ign") {
		if (extras.length === 0) throw new Error("Did not provide UUID to whitelist");
		const ign = extras;
		const uuid = metadata.lookup_uuid(ign);
		if (uuid == null) throw new Error("No cached UUID for IGN " + ign);
		await metadata.whitelist_remove(uuid);
	}

	else {
		throw new Error(`Unknown command "${command}"`);
	}
}

function input_loop() {
	console.log("===========================================================");
	term.question(">", (input: string) => handle_input(input.trim()).catch(e => { console.error("Command failed:"); console.error(e); }).finally(input_loop));
}
input_loop();
