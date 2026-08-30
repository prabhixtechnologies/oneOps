import { z } from "zod";

// GET /flags returns FlagDetail entries, not a key/boolean map. The map form exists on the server as
// a separate DTO that this endpoint does not use, so the record shape here never matched and the
// Feature flags page could not render at all.
export const flagDetailSchema = z.object({
  key: z.string(),
  enabled: z.boolean(),
  // "DEFAULT" or "OVERRIDE" today; left as a string so a new source does not break the page.
  source: z.string(),
  description: z.string().nullish(),
});

export const effectiveFlagsSchema = z.object({
  flags: z.array(flagDetailSchema),
});

export type FlagDetail = z.infer<typeof flagDetailSchema>;
