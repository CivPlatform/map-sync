import "./cli";
import { connectDB } from "./db";
import { TcpServer } from "./server/server";

// Have to do this because node doesn't have top-level await for CommonJS
Promise.resolve().then(async () => {
    await connectDB();
    const server = new TcpServer();
});
