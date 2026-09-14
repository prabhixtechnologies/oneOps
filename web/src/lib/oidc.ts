import { IS_ADMIN_APP } from "./app-mode";
import { safeAppPath } from "./safePath";
import {
  beginLogin,
  beginLogout,
  beginSignup,
  completeLogin,
  configureOidc,
  handleCallback,
  idToken,
  identityIssuer,
  isOidcEnabled,
  redirectUri,
  rememberIdToken,
} from "@prabhix/oidc-client";

/**
 * Product wrapper: PKCE lives in `@prabhix/oidc-client`. This file only picks the Identity client
 * id for this bundle (admin vs console) and the in-app return-path rules.
 */
configureOidc({
  issuer: import.meta.env.VITE_IDENTITY_ISSUER ?? "",
  clientId: IS_ADMIN_APP ? "prabhix-admin" : "prabhix-console",
  safeReturnTo: safeAppPath,
});

export {
  beginLogin,
  beginLogout,
  beginSignup,
  completeLogin,
  handleCallback,
  idToken,
  identityIssuer,
  isOidcEnabled,
  redirectUri,
  rememberIdToken,
};

export type { OidcTokens } from "@prabhix/oidc-client";
