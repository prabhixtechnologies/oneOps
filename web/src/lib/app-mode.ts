/**
 * Which of the two consoles this bundle is.
 *
 * <p>A compile-time constant, substituted by Vite's `define`. The two apps share one source tree but
 * are two separate builds, so each gets its own value — and because the comparisons below fold to
 * `true` or `false` before minification, the branches for the other app are removed entirely rather
 * than shipped and skipped at runtime.
 *
 * <p>That is the point. Held in a React context instead, the customer product's bundle would still
 * contain the platform admin navigation and the cross-organization log controls, unreachable but
 * readable by anyone who opens the sources tab — telling a customer exactly which staff-only
 * endpoints exist. Guards on the server are what actually stop the calls; this stops the invitation.
 */
export type AppMode = "admin" | "oneops";

export const APP_MODE: AppMode = __APP_MODE__;

/** The private, platform-wide console. Constant-folds away in the OneOps build. */
export const IS_ADMIN_APP = APP_MODE === "admin";
