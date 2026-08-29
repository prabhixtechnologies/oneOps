/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_URL: string;
  readonly VITE_RAZORPAY_KEY_ID: string;
  readonly VITE_GOOGLE_SSO_ENABLED: string;
  /** Admin build only: where "View as" sends staff. See lib/staff-handoff.ts. */
  readonly VITE_ONEOPS_URL?: string;
  /** Prabhix Mailroom, for personal mail. Unset hides the link rather than guessing a host. */
  readonly VITE_MAILROOM_URL?: string;
  /** Identity's issuer. Blank falls back to the native password form. See lib/oidc.ts. */
  readonly VITE_IDENTITY_ISSUER?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

/** Substituted by Vite's `define` at build time. See lib/app-mode.ts. */
declare const __APP_MODE__: "admin" | "oneops";
