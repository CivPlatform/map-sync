import lib_readline from "readline";
import lib_stream from "stream";

import * as metadata from "./metadata";

//idk where these come from lol
interface TerminalExtras {
    output: lib_stream.Writable;
    _refreshLine(): void;
}
type TermType = lib_readline.Interface & TerminalExtras;
const term = lib_readline.createInterface({
    input: process.stdin,
    output: process.stdout,
}) as TermType;

if (!("MAPSYNC_DUMB_TERM" in process.env)) {
    //Adapted from https://stackoverflow.com/questions/10606814/readline-with-console-log-in-the-background/10608048#10608048
    function fixStdoutFor(term: TermType) {
        var oldStdout = process.stdout;
        var newStdout = Object.create(oldStdout);
        var oldStderr = process.stderr;
        var newStderr = Object.create(oldStdout);
        function write_func(outout: lib_stream.Writable) {
            return function (this: lib_stream.Writable) {
                term.output.write("\x1b[2K\r");
                var result = outout.write.apply(
                    this,
                    Array.prototype.slice.call(arguments) as any,
                );
                term._refreshLine();
                return result;
            };
        }
        newStdout.write = write_func(oldStdout);
        newStderr.write = write_func(oldStderr);
        Object.defineProperty(process, "stdout", {
            get: function () {
                return newStdout;
            },
        });
        Object.defineProperty(process, "stderr", {
            get: function () {
                return newStderr;
            },
        });
    }
    fixStdoutFor(term);
    const old_log = console.log;
    console.log = function () {
        term.output.write("\x1b[2K\r");
        old_log.apply(this, arguments as any);
        term._refreshLine();
    };
    const old_error = console.error;
    console.error = function () {
        term.output.write("\x1b[2K\r");
        old_error.apply(this, arguments as any);
        term._refreshLine();
    };
}

async function handle_input(input: string): Promise<void> {
    const command_end_i = input.indexOf(" ");
    const command = (
        command_end_i > -1 ? input.substring(0, command_end_i) : input
    ).toLowerCase();
    const extras = command_end_i > -1 ? input.substring(command_end_i + 1) : "";

    if (command === "") {
    } else if (command === "ping") console.log("pong");
    else if (command === "help") {
        console.log('ping - Prints "pong" for my sanity. -SirAlador');
        console.log(
            "help - Prints info about commands, including the help command.",
        );
        console.log("whitelist_load - Loads the whitelist from disk");
        console.log("whitelist_save - Saves the whitelist to disk");
        console.log(
            "whitelist_add <uuid> - Adds the given account UUID to the\n    whitelist, and saves the whitelist to disk",
        );
        console.log(
            "whitelist_add_ign <ign> - Adds the UUID cached with the\n    given IGN to the whitelist, and saves the whitelist to disk",
        );
        console.log(
            "whitelist_remove <uuid> - Removes the given account UUID\n    from the whitelist, and saves the whitelist to disk",
        );
        console.log(
            "whitelist_remove_ign <ign> - Removes the UUID cached with\n    the given IGN from the whitelist, and saves the whitelist to disk",
        );
    } else if (command === "whitelist_load") metadata.whitelist_load();
    else if (command === "whitelist_save") metadata.whitelist_save();
    else if (command === "whitelist_add") {
        if (extras.length === 0)
            throw new Error("Did not provide UUID to whitelist");
        const uuid = extras;
        metadata.whitelist.add(uuid);
        metadata.whitelist_save();
    } else if (command === "whitelist_add_ign") {
        if (extras.length === 0)
            throw new Error("Did not provide UUID to whitelist");
        const ign = extras;
        const uuid = metadata.uuid_cache_lookup(ign);
        if (uuid == null) throw new Error("No cached UUID for IGN " + ign);
        metadata.whitelist.add(uuid);
        metadata.whitelist_save();
    } else if (command === "whitelist_remove") {
        if (extras.length === 0)
            throw new Error("Did not provide UUID to whitelist");
        const uuid = extras;
        metadata.whitelist.delete(uuid);
        metadata.whitelist_save();
    } else if (command === "whitelist_remove_ign") {
        if (extras.length === 0)
            throw new Error("Did not provide UUID to whitelist");
        const ign = extras;
        const uuid = metadata.uuid_cache_lookup(ign);
        if (uuid == null) throw new Error("No cached UUID for IGN " + ign);
        metadata.whitelist.delete(uuid);
        metadata.whitelist_save();
    } else {
        throw new Error(`Unknown command "${command}"`);
    }
}

function input_loop() {
    console.log("===========================================================");
    term.question(">", (input: string) =>
        handle_input(input.trim())
            .catch((e) => {
                console.error("Command failed:");
                console.error(e);
            })
            .finally(input_loop),
    );
}
input_loop();
