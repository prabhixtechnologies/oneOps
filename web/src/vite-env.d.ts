/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_URL: string;
  readonly VITE_RAZORPAY_KEY_ID: string;
  readonly VITE_GOOGLE_SSO_ENABLED: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
