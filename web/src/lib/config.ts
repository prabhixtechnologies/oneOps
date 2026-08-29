const RAW_API_BASE = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

/**
 * The API origin, without a trailing slash or version segment.
 *
 * VITE_API_URL is meant to carry an origin only, because {@link API_V1} adds the version. The
 * Android client's equivalent setting takes the opposite convention and includes `/api/v1`, and
 * building the console with that form produced requests to `/api/v1/api/v1/...`. Those do not
 * fail as a wrong path: nothing matches, so the server treats them as a protected endpoint and
 * answers "Authentication is required", which looks like a credentials problem and sends you
 * looking in entirely the wrong place. Normalising here is cheaper than expecting everyone to
 * remember which of the two conventions applies where.
 */
export const API_BASE = RAW_API_BASE.replace(/\/+$/, "").replace(/\/api\/v1$/, "");

export const API_V1 = `${API_BASE}/api/v1`;

export const RAZORPAY_KEY_ID = import.meta.env.VITE_RAZORPAY_KEY_ID ?? "";

export const GOOGLE_SSO_ENABLED =
  import.meta.env.VITE_GOOGLE_SSO_ENABLED === "true";
