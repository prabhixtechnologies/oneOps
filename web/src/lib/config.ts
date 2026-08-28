export const API_BASE =
  import.meta.env.VITE_API_URL ?? "http://localhost:8080";

export const API_V1 = `${API_BASE}/api/v1`;

export const RAZORPAY_KEY_ID = import.meta.env.VITE_RAZORPAY_KEY_ID ?? "";

export const GOOGLE_SSO_ENABLED =
  import.meta.env.VITE_GOOGLE_SSO_ENABLED === "true";
