import * as z from "zod";
import { parseConfigFile } from "./mod";

export type Config = z.infer<typeof ConfigSchema>;
export const ConfigSchema = z.object({
    gameAddress: z.string(),
    whitelist: z.boolean().default(true)
});

let config: Config | null = null;
/**
 * Retrieves the config, loading it if it's not already loaded.
 */
export function get(): Config {
    if (config === null) {
        config = parseConfigFile("config.json", ConfigSchema.parse, () => ({
            gameAddress: "localhost:25565",
            whitelist: true
        }));
    }
    return config;
}
