-- CreateTable
CREATE TABLE "chunk_data" (
    "hash" BLOB NOT NULL PRIMARY KEY,
    "version" INTEGER NOT NULL,
    "data" BLOB NOT NULL
);

-- CreateTable
CREATE TABLE "player_chunk" (
    "world" TEXT NOT NULL,
    "chunk_x" INTEGER NOT NULL,
    "chunk_z" INTEGER NOT NULL,
    "uuid" TEXT NOT NULL,
    "ts" BIGINT NOT NULL,
    "hash" BLOB,

    PRIMARY KEY ("world", "chunk_x", "chunk_z", "uuid"),
    CONSTRAINT "player_chunk_hash_fkey" FOREIGN KEY ("hash") REFERENCES "chunk_data" ("hash") ON DELETE NO ACTION ON UPDATE NO ACTION
);
