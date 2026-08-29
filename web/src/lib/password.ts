import { z } from "zod";

/**
 * The password rule, in one place.
 *
 * The API enforces `@Size(min = 10)` on every password it accepts — registration, invite
 * acceptance, reset and change. The four auth forms each carried their own copy of the number and
 * the sentence, and the change-password form in Settings carried neither, so it submitted a short
 * password, got back a generic "Some fields need attention", and gave no way to work out why.
 * Keep this in step with UserDtos and AuthDtos on the server.
 */
export const PASSWORD_MIN_LENGTH = 10;

export const PASSWORD_RULE = `Password must be at least ${PASSWORD_MIN_LENGTH} characters`;

export const passwordSchema = z.string().min(PASSWORD_MIN_LENGTH, PASSWORD_RULE);
