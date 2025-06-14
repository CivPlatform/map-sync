import { test } from "bun:test";

test("testMigrations", async () => {
    process.env["SQLITE_PATH"] = ":memory:"; // Ensure an in-memory database

    const { setup } = await require("./database.ts");
    await setup();
});
