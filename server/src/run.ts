import lib_fs from "node:fs";
import { ErrorType, getErrorType } from "./deps/errors";
import { DATA_FOLDER } from "./config/mod";
import * as database from "./database";
import { TcpServer } from "./server/server";
import * as config from "./config/config";
import * as whitelist from "./config/whitelist";
import * as uuid_cache from "./config/uuid_cache";

// Have to do this because node doesn't have top-level await for CommonJS
Promise.resolve().then(async () => {
    try {
        lib_fs.mkdirSync(DATA_FOLDER, { recursive: true });
        console.log(`Created data folder "${DATA_FOLDER}"`);
    } catch (e: any) {
        if (getErrorType(e) !== ErrorType.FileExists) throw e;
        console.log(`Using data folder "${DATA_FOLDER}"`);
    }

    // Initially load configs
    config.get();
    whitelist.load();
    uuid_cache.load();

    await database.setup();
    const server = new TcpServer();
});

// TODO: Fix mod resource filtering so the version is correctly set

// TODO: Fix mod not disconnecting from MapSync when disconnected from MC server

// TODO: Re-add runtime commands in some capacity
