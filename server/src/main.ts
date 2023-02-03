import "./cli";
import { connectDB } from "./database";
import { TcpServer } from "./server/server";
import * as config from "./config/config";
import * as whitelist from "./config/whitelist";

// Have to do this because node doesn't have top-level await for CommonJS
Promise.resolve().then(async () => {
    // Initially load configs
    config.get();
    whitelist.load();

    await connectDB();
    const server = new TcpServer();
});

// TODO: Fix mod resource filtering so the version is correctly set

// TODO: Fix mod not disconnecting from MapSync when disconnected from MC server