import { test } from "bun:test";

import DatabaseConnection from "./database.ts";

test("testMigrations", async () => {
    const database = new DatabaseConnection(":memory:");
    await database.setup();
});
