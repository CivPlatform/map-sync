import lib_fs from "fs";
import { getErrorType, ErrorType } from "./deps/errors";
import { parseConfigFile, saveConfigFile } from "./config/mod";
import * as z from "zod";

export const DATA_FOLDER = process.env["MAPSYNC_DATA_DIR"] ?? "./mapsync";
try {
    lib_fs.mkdirSync(DATA_FOLDER, { recursive: true });
    console.log(`Created data folder "${DATA_FOLDER}"`);
} catch (e: any) {
    if (getErrorType(e) !== ErrorType.FileExists) throw e;
    console.log(`Using data folder "${DATA_FOLDER}"`);
}
