import { IS_ADMIN_APP } from "./app-mode";

/**
 * The authorization code flow with PKCE, against Prabhix Identity.
 *
 * <p>This replaces posting a password to an API. The difference is not cosmetic: with a hosted login
 * page, this app never sees a credential, so a cross-site scripting hole here cannot steal one — and
 * the session cookie set by the identity origin is what lets the second product sign somebody in
 * without asking again. That is single sign-on; two apps each with their own login form is two logins.
 *
 * <p>PKCE rather than a client secret because a browser app cannot keep a secret: anything shipped to
 * the browser is readable by whoever receives it. The verifier is generated per attempt, never leaves
 * this origin, and is what proves the app redeeming the code is the one that requested it.
 */

const ISSUER: string = import.meta.env.VITE_IDENTITY_ISSUER ?? "";

/** Matches the client ids seeded by identity's RegisteredClientSeeder. */
const CLIENT_ID = IS_ADMIN_APP ? "prabhix-admin" : "prabhix-console";

export function identityIssuer(): string {
  return ISSUER.replace(/\/+$/, "");
}

export function isOidcEnabled(): boolean {
  return identityIssuer().length > 0;
}

// sessionStorage, not localStorage. The verifier is single-use and worthless after the exchange, and a
// per-tab scope means two sign-in attempts in two tabs cannot overwrite each other's — which
// localStorage would do, breaking whichever tab finished second.
const VERIFIER_KEY = "pbx_pkce_verifier";
const STATE_KEY = "pbx_oauth_state";
const RETURN_KEY = "pbx_oauth_return_to";
// Kept because sign-out needs it: /connect/logout identifies the session to end from the id token,
// and without one the provider has nothing to act on. It survives a reload, which matters — a person
// who refreshes the page and then signs out is the ordinary case, not an edge one.
const ID_TOKEN_KEY = "pbx_id_token";

export function redirectUri(): string {
  return `${window.location.origin}/auth/callback`;
}

/**
 * Sends the browser to the hosted login page.
 *
 * @param returnTo where to land afterwards, remembered locally rather than round-tripped through the
 *     provider. Putting it in the `state` parameter would make it attacker-controlled, and a
 *     post-login redirect an attacker chooses is an open redirect wearing a different hat.
 */
export async function beginLogin(returnTo?: string): Promise<void> {
  await authorize(returnTo);
}

/**
 * Sends the browser to the hosted signup page, the long way round.
 *
 * <p>Through `/authorize` with `prompt=create` rather than straight to the signup URL, because the
 * redirect is what makes the provider save this authorization request. With one saved, a person who
 * signs up from the admin console is returned to the admin console; without one, the provider has
 * only a configured default to fall back on and everybody lands in the same product.
 *
 * <p>`prompt=create` is OpenID Connect's registration extension. A provider that does not implement
 * it ignores the parameter and shows the login page, which has a link to signup on it — degraded, not
 * broken.
 */
export async function beginSignup(returnTo?: string): Promise<void> {
  await authorize(returnTo, "create");
}

async function authorize(returnTo?: string, prompt?: string): Promise<void> {
  const verifier = randomUrlSafe(64);
  const state = randomUrlSafe(32);

  sessionStorage.setItem(VERIFIER_KEY, verifier);
  sessionStorage.setItem(STATE_KEY, state);
  if (returnTo) sessionStorage.setItem(RETURN_KEY, returnTo);

  const params = new URLSearchParams({
    response_type: "code",
    client_id: CLIENT_ID,
    redirect_uri: redirectUri(),
    scope: "openid profile email",
    state,
    code_challenge: await sha256Base64Url(verifier),
    // S256, never "plain". Plain sends the verifier itself in the redirect, so anything that can read
    // the authorization request can also redeem the code, which is the attack PKCE exists to stop.
    code_challenge_method: "S256",
  });
  if (prompt) params.set("prompt", prompt);

  window.location.assign(`${identityIssuer()}/oauth2/authorize?${params.toString()}`);
}

export interface OidcTokens {
  accessToken: string;
  expiresIn: number;
  idToken?: string;
}

/**
 * Redeems the code the provider sent back.
 *
 * @throws if `state` does not match what this tab stored, which means the response belongs to a flow
 *     this tab did not start — a forged callback, or a stale one from another tab.
 */
export async function completeLogin(search: URLSearchParams): Promise<{
  tokens: OidcTokens;
  returnTo: string;
}> {
  const error = search.get("error");
  if (error) {
    throw new Error(search.get("error_description") ?? describeOauthError(error));
  }

  const code = search.get("code");
  const state = search.get("state");
  const expectedState = sessionStorage.getItem(STATE_KEY);
  const verifier = sessionStorage.getItem(VERIFIER_KEY);
  const returnTo = sessionStorage.getItem(RETURN_KEY) ?? "/";

  // Cleared before the exchange, not after. The code is single-use, so a retry with the same verifier
  // would fail anyway, and leaving them behind means a later forged callback finds a usable verifier.
  sessionStorage.removeItem(VERIFIER_KEY);
  sessionStorage.removeItem(STATE_KEY);
  sessionStorage.removeItem(RETURN_KEY);

  if (!code || !state || !verifier) {
    throw new Error("That sign-in link is incomplete. Start again from the sign-in page.");
  }
  if (!expectedState || state !== expectedState) {
    throw new Error("That sign-in response did not match this browser. Start again.");
  }

  const body = new URLSearchParams({
    grant_type: "authorization_code",
    code,
    redirect_uri: redirectUri(),
    client_id: CLIENT_ID,
    code_verifier: verifier,
  });

  const response = await fetch(`${identityIssuer()}/oauth2/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    // The refresh token comes back in a cookie set by the identity origin, so this request has to
    // carry and accept cookies. Keeping the refresh token out of JavaScript is the point: script on
    // this page cannot read an HttpOnly cookie, so an XSS hole cannot walk away with a 30-day token.
    credentials: "include",
    body,
  });

  if (!response.ok) {
    throw new Error("Could not complete sign-in. Start again from the sign-in page.");
  }

  const payload = (await response.json()) as {
    access_token: string;
    expires_in: number;
    id_token?: string;
  };

  return {
    tokens: {
      accessToken: payload.access_token,
      expiresIn: payload.expires_in,
      idToken: payload.id_token,
    },
    returnTo,
  };
}

/** Remembers the id token from a completed sign-in, so sign-out has something to present. */
export function rememberIdToken(idToken?: string): void {
  if (idToken) sessionStorage.setItem(ID_TOKEN_KEY, idToken);
}

/**
 * Ends the session at the provider, not only here.
 *
 * <p>Clearing local state alone would leave the identity session cookie in place, so the next
 * `/authorize` returns a code immediately and the person appears to be signed straight back in —
 * which reads as a broken sign-out button rather than the security hole it is on a shared machine.
 * This function existed and nothing called it, so that is precisely what signing out did.
 *
 * <p>Navigates away, so it has to be the last thing a caller does.
 */
export function beginLogout(): void {
  const idToken = sessionStorage.getItem(ID_TOKEN_KEY);
  sessionStorage.removeItem(ID_TOKEN_KEY);

  const params = new URLSearchParams({ client_id: CLIENT_ID });
  if (idToken) {
    params.set("id_token_hint", idToken);
    // Only sent alongside a hint. The provider validates it against the hint's client, so on its own
    // it is rejected — and being bounced back here would look like a sign-out that did nothing.
    params.set("post_logout_redirect_uri", `${window.location.origin}/`);
  }
  window.location.assign(`${identityIssuer()}/connect/logout?${params.toString()}`);
}

function describeOauthError(code: string): string {
  switch (code) {
    case "access_denied":
      return "Sign-in was cancelled.";
    case "invalid_request":
    case "invalid_client":
      // Almost always a misconfigured redirect URI, which is a deploy problem rather than the
      // visitor's. Saying "try again" would send them round a loop that cannot succeed.
      return "This application is not configured correctly for sign-in. Contact support.";
    default:
      return "Sign-in did not complete. Start again from the sign-in page.";
  }
}

function randomUrlSafe(bytes: number): string {
  const buffer = new Uint8Array(bytes);
  crypto.getRandomValues(buffer);
  return base64UrlEncode(buffer);
}

async function sha256Base64Url(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return base64UrlEncode(new Uint8Array(digest));
}

/** Base64url per RFC 7636: no padding, and the two substituted characters. */
function base64UrlEncode(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
