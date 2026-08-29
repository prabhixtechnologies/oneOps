import { z } from "zod";

/**
 * Schema conventions for this directory.
 *
 * The API is configured with Jackson's `non_null` inclusion, so a field that is null is left out of
 * the JSON rather than sent as `"field": null`. Response schemas must therefore accept both forms:
 *
 *   - use `.nullish()` for anything the server may leave unset — it allows absent *and* null
 *   - `.nullable()` alone is wrong here: it demands the key, so it fails on the exact rows where the
 *     value is missing, which are usually the ordinary ones (unassigned thread, unused API key,
 *     system activity with no actor, an org that is not on trial)
 *   - `.optional()` alone is also risky, because it rejects an explicit null
 *
 * These bugs stay invisible until real data shows up and then present as a blank error page, so
 * prefer `.nullish()` unless the field is genuinely always present. Request schemas are exempt:
 * there `.optional()` means "leave unchanged" and `.nullable()` means "clear it", and the difference
 * matters.
 */
export const apiErrorSchema = z.object({
  code: z.string(),
  message: z.string(),
  fieldErrors: z.record(z.string(), z.string()).optional(),
  traceId: z.string().optional(),
  path: z.string().optional(),
  timestamp: z.string().optional(),
});

export type ApiError = z.infer<typeof apiErrorSchema>;

export function cursorPageSchema<T extends z.ZodTypeAny>(itemSchema: T) {
  return z.object({
    items: z.array(itemSchema),
    // nullish, not nullable: the last page has no cursor, and a server configured to omit nulls
    // sends no key at all. Both mean "no more pages", so neither should throw.
    nextCursor: z
      .string()
      .nullish()
      .transform((value) => value ?? null),
    hasMore: z.boolean().default(false),
  });
}

export function pageResponseSchema<T extends z.ZodTypeAny>(itemSchema: T) {
  return z.object({
    items: z.array(itemSchema),
    page: z.number(),
    size: z.number(),
    totalItems: z.number(),
    totalPages: z.number(),
    hasNext: z.boolean(),
    hasPrevious: z.boolean(),
  });
}

export function arraySchema<T extends z.ZodTypeAny>(itemSchema: T) {
  return z.array(itemSchema);
}

export const authTokensSchema = z.object({
  accessToken: z.string(),
  refreshToken: z.string(),
  expiresInSeconds: z.number(),
  organizationId: z.string().optional(),
  permissions: z.array(z.string()).optional(),
});

export const authMeSchema = z.object({
  userId: z.string(),
  email: z.string(),
  displayName: z.string(),
  organizationId: z.string(),
  sessionId: z.string(),
  permissions: z.array(z.string()),
  platformAdmin: z.boolean(),
});

export const userProfileSchema = z.object({
  id: z.string(),
  email: z.string(),
  emailVerified: z.boolean(),
  fullName: z.string(),
  // Optional in the DTO and unset for most accounts — fall back to fullName when showing a name.
  displayName: z.string().nullish(),
  avatarUrl: z.string().nullable().optional(),
  jobTitle: z.string().nullable().optional(),
  timezone: z.string().nullable().optional(),
  locale: z.string().nullable().optional(),
  status: z.string(),
  platformAdmin: z.boolean(),
  defaultOrganizationId: z.string().nullable().optional(),
  notificationPrefs: z.record(z.string(), z.unknown()).optional(),
  createdAt: z.string(),
});

export const organizationViewSchema = z.object({
  id: z.string(),
  name: z.string(),
  slug: z.string(),
  status: z.string(),
  memberCount: z.number(),
  seatLimit: z.number(),
  trialEndsAt: z.string().nullish(),
  timezone: z.string().nullable().optional(),
  locale: z.string().nullable().optional(),
  currency: z.string().nullable().optional(),
  createdAt: z.string(),
});

export const ackResponseSchema = z.object({
  message: z.string().optional(),
});

export type AuthMe = z.infer<typeof authMeSchema>;
export type AuthTokens = z.infer<typeof authTokensSchema>;
export type UserProfile = z.infer<typeof userProfileSchema>;
export type OrganizationView = z.infer<typeof organizationViewSchema>;

export type CursorPage<T> = {
  items: T[];
  nextCursor: string | null;
  hasMore: boolean;
};

export type PageResponse<T> = {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
};
