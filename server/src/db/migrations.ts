import { Kysely, sql, type Migration, type MigrationProvider } from "kysely";

type MigrationRegistry = Record<string, Migration>;
type MigrationClass = { name: string } & (new () => Migration);

export default class Migrations implements MigrationProvider {
    public async getMigrations(): Promise<MigrationRegistry> {
        return this.generateMigrationRegistry([
            Migration_0001_InitialSetup,
            Migration_0002_GenerateRegionCoordColumns,
        ]);
    }

    private generateMigrationRegistry(
        migrations: Array<MigrationClass>,
    ): MigrationRegistry {
        const registry: MigrationRegistry = {};
        for (const clazz of migrations) {
            registry[clazz.name] = new clazz();
        }
        return registry;
    }
}

// ============================================================
// WARNING FOR WRITING MIGRATIONS!
//
// Kysely does not respect class functions: your "up" and "down" methods MUST
// be fields, not class functions, otherwise your migration will fail!
// ============================================================

export class Migration_0001_InitialSetup implements Migration {
    public up = async (db: Kysely<any>) => {
        await db.transaction().execute(async (transaction) => {
            await transaction.schema
                .createTable("chunk_data")
                .ifNotExists()
                .addColumn("hash", "blob", (col) => col.notNull().primaryKey())
                .addColumn("version", "integer", (col) => col.notNull())
                .addColumn("data", "blob", (col) => col.notNull())
                .execute();
            await transaction.schema
                .createTable("player_chunk")
                .ifNotExists()
                .addColumn("world", "text", (col) => col.notNull())
                .addColumn("chunk_x", "integer", (col) => col.notNull())
                .addColumn("chunk_z", "integer", (col) => col.notNull())
                .addColumn("uuid", "text", (col) => col.notNull())
                .addColumn("ts", "bigint", (col) => col.notNull())
                .addColumn("hash", "blob", (col) => col.notNull())
                .addPrimaryKeyConstraint("PK_coords_and_player", [
                    "world",
                    "chunk_x",
                    "chunk_z",
                    "uuid",
                ])
                .addForeignKeyConstraint(
                    "FK_chunk_ref",
                    ["hash"],
                    "chunk_data",
                    ["hash"],
                    (fk) => fk.onUpdate("no action").onDelete("no action"),
                )
                .execute();
        });
    };
    // Probably shouldn't define a "down" since that just means an empty db
}

export class Migration_0002_GenerateRegionCoordColumns implements Migration {
    public up = async (db: Kysely<any>) => {
        await db.transaction().execute(async (transaction) => {
            await transaction.schema
                .alterTable("player_chunk")
                .addColumn("gen_region_x", "integer", (col) => {
                    return col
                        .generatedAlwaysAs(sql<number>`floor(chunk_x / 32.0)`)
                        .notNull();
                })
                .execute();
            await transaction.schema
                .alterTable("player_chunk")
                .addColumn("gen_region_z", "integer", (col) => {
                    return col
                        .generatedAlwaysAs(sql<number>`floor(chunk_z / 32.0)`)
                        .notNull();
                })
                .execute();
            await transaction.schema
                .alterTable("player_chunk")
                .addColumn("gen_region_coord", "text", (col) => {
                    return col
                        .generatedAlwaysAs(
                            sql<string>`gen_region_x || '_' || gen_region_z`,
                        )
                        .notNull();
                })
                .execute();
        });
    };
    public down = async (db: Kysely<any>) => {
        await db.transaction().execute(async (transaction) => {
            await transaction.schema
                .alterTable("player_chunk")
                .dropColumn("gen_region_coord")
                .execute();
            await transaction.schema
                .alterTable("player_chunk")
                .dropColumn("gen_region_x")
                .execute();
            await transaction.schema
                .alterTable("player_chunk")
                .dropColumn("gen_region_z")
                .execute();
        });
    };
}
