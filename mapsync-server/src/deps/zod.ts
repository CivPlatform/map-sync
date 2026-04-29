export * from "zod";
export { fromZodError } from "zod-validation-error";

import * as z from "zod";
import { createOfflineUuid } from "./uuid.ts";

export function offlineUuid() {
    return z.preprocess((val) => {
        if (typeof val !== "string") {
            return val;
        }
        const match = /^AUTH-DISABLED-(.+)/.exec(val) ?? null;
        if (match === null) {
            return val;
        }
        return createOfflineUuid(match[1]);
    }, z.string().uuid());
}
