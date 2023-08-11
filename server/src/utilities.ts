import { promises as fs_promises } from "fs";

/**
 * Attempts to load a text-file from the given path. If the file does not exist then the given fallback text is saved
 * into a new text-file at the given path.
 *
 * @param {string} path The path of the text-file.
 * @param {string} fallback The text to default to should the text-file not exist.
 * @return {string} Returns the contents of the text-file, or the fallback text.
 *
 * @author Protonull
 */
export async function loadOrSaveDefaultStringFile(path: string, fallback: string): Promise<string> {
    try {
        return await fs_promises.readFile(path, { encoding: "utf8" });
    } catch (thrown) {}
    try {
        await fs_promises.writeFile(path, fallback, { encoding: "utf8" });
    } catch (thrown) {
        console.warn("Could not create default file for [" + path + "]");
    }
    return fallback;
}
