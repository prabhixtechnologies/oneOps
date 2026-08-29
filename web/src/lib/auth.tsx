import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiClientError, apiRequest, configureApiClient } from "./api-client";
import { setViewingOrg, viewingOrgId } from "./impersonation";
import {
  arraySchema,
  authMeSchema,
  authTokensSchema,
  organizationViewSchema,
  userProfileSchema,
  type AuthMe,
  type OrganizationView,
  type UserProfile,
} from "./schemas/common";

/**
 * Exchanges the shared session cookie for an access token.
 *
 * <p>This replaced a refresh token kept in localStorage, for two reasons. localStorage is scoped to
 * one origin, so the admin console could not see a session established on the OneOps console and
 * demanded its own login; and anything in localStorage is readable by injected script, whereas the
 * cookie behind this endpoint is HttpOnly and cannot be read at all.
 */
const SESSION_TOKEN_PATH = "/auth/session/token";

interface AuthState {
  accessToken: string | null;
  me: AuthMe | null;
  profile: UserProfile | null;
  organization: OrganizationView | null;
  isLoading: boolean;
  isAuthenticated: boolean;
}

interface AuthContextValue extends AuthState {
  login: (email: string, password: string) => Promise<void>;
  loginWithTokens: (accessToken: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshSession: () => Promise<boolean>;
  switchOrg: (orgId: string) => Promise<void>;
  permissions: string[];
  userId: string | null;
  organizationId: string | null;
}

const AuthContext = createContext<AuthContextValue | null>(null);

async function fetchMe(): Promise<AuthMe> {
  return apiRequest("/auth/me", authMeSchema);
}

async function fetchProfile(): Promise<UserProfile> {
  return apiRequest("/users/me", userProfileSchema);
}

async function fetchOrganization(id: string): Promise<OrganizationView> {
  return apiRequest(`/organizations/${id}`, organizationViewSchema);
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [accessToken, setAccessTokenState] = useState<string | null>(null);
  const [me, setMe] = useState<AuthMe | null>(null);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [organization, setOrganization] = useState<OrganizationView | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // The token lives in a ref as well as state. The ref is what the callbacks below read; the state
  // exists only so the tree re-renders. Reading it from state inside a callback would change that
  // callback's identity on every renewal, and since one of those callbacks is a dependency of the
  // bootstrap effect, renewing a token would re-trigger the bootstrap and renew again.
  const accessTokenRef = useRef<string | null>(null);
  const orgIdRef = useRef<string | null>(null);

  // Set when the server has said there is no session for this browser. Without it, every query
  // that 401s starts its own exchange: the single-flight guard below only collapses *concurrent*
  // attempts, so a page with several queries walks through them one at a time and earns a 429.
  //
  // Deliberately only set on a refusal. A network error or a 500 is temporary, and treating it as
  // "signed out" would strand a signed-in person on the login page until they reloaded.
  const sessionGone = useRef(false);

  const setAccessToken = useCallback((token: string | null) => {
    accessTokenRef.current = token;
    setAccessTokenState(token);
    if (token) sessionGone.current = false;
  }, []);

  const loadSession = useCallback(async () => {
    const authMe = await fetchMe();
    // Set before the parallel fetches so they carry the org header on their first attempt.
    orgIdRef.current = authMe.organizationId;
    setMe(authMe);
    const [userProfile, org] = await Promise.all([
      fetchProfile().catch(() => null),
      fetchOrganization(authMe.organizationId).catch(() => null),
    ]);
    setProfile(userProfile);
    setOrganization(org);
    return authMe;
  }, []);

  const applyTokens = useCallback(
    async (access: string) => {
      setAccessToken(access);
      await loadSession();
    },
    [loadSession, setAccessToken],
  );

  /** Drops local credentials without touching the network. */
  const clearSession = useCallback(() => {
    setAccessToken(null);
    setMe(null);
    setProfile(null);
    setOrganization(null);
    orgIdRef.current = null;
    // Otherwise the next person to sign in on this tab lands inside whichever customer's data the
    // last one was looking at.
    setViewingOrg(null);
    queryClient.clear();
  }, [queryClient, setAccessToken]);

  const logout = useCallback(async () => {
    try {
      if (accessTokenRef.current) {
        // The server clears the cookie on this call, so the other console is signed out too.
        await apiRequest("/auth/logout", { parse: () => undefined }, { method: "POST" });
      }
    } catch {
      // ignore logout errors
    }
    sessionGone.current = true;
    clearSession();
  }, [clearSession]);

  // Collapses concurrent exchanges into one request. The cookie does not rotate, so several
  // exchanges would all succeed — this is about not firing one per query on a page that has just
  // found its access token expired.
  const refreshInFlight = useRef<Promise<boolean> | null>(null);

  // Deliberately does not reload the session: doing that inside the single-flight promise meant a
  // 401 from /auth/me would ask for a refresh, be handed back the very promise that was waiting on
  // it, and deadlock — leaving refreshInFlight set forever so no later refresh could run either.
  const refreshSession = useCallback(async (): Promise<boolean> => {
    const existing = refreshInFlight.current;
    if (existing) return existing;
    if (sessionGone.current) return false;

    const attempt = (async () => {
      try {
        const tokens = await apiRequest(SESSION_TOKEN_PATH, authTokensSchema, {
          method: "POST",
          // skipAuth matters beyond tidiness: it stops a 401 here from triggering the client's own
          // refresh-and-retry, which would call straight back into this function.
          skipAuth: true,
          skipOrg: true,
        });
        setAccessToken(tokens.accessToken);
        return true;
      } catch (err) {
        if (err instanceof ApiClientError && (err.status === 401 || err.status === 403)) {
          sessionGone.current = true;
        }
        return false;
      } finally {
        refreshInFlight.current = null;
      }
    })();

    refreshInFlight.current = attempt;
    return attempt;
  }, [setAccessToken]);

  /**
   * Adopts an access token obtained by a page that authenticated on its own — sign-up, magic link
   * and one-time code all call their endpoint directly. Those responses set the session cookie
   * server-side, so nothing needs storing here beyond the access token itself.
   */
  const loginWithTokens = useCallback(
    async (access: string) => {
      await applyTokens(access);
    },
    [applyTokens],
  );

  const login = useCallback(
    async (email: string, password: string) => {
      const tokens = await apiRequest(
        "/auth/login",
        authTokensSchema,
        {
          method: "POST",
          body: { email, password },
          skipAuth: true,
          skipOrg: true,
        },
      );
      await applyTokens(tokens.accessToken);
    },
    [applyTokens],
  );

  const switchOrg = useCallback(
    async (orgId: string) => {
      // Changing the session's own organization makes any viewing override meaningless, and leaving
      // it set would silently keep sending the old customer's id after the switch.
      setViewingOrg(null);
      const tokens = await apiRequest(
        `/organizations/${orgId}/select`,
        authTokensSchema,
        { method: "POST" },
      );
      await applyTokens(tokens.accessToken);
    },
    [applyTokens],
  );

  useEffect(() => {
    configureApiClient({
      getAccessToken: () => accessTokenRef.current,
      // A platform admin viewing a customer's data overrides the org for the duration. The session
      // itself is untouched — only which tenant's rows each request asks for.
      getOrgId: () => viewingOrgId() ?? orgIdRef.current,
      refreshTokens: refreshSession,
      // Local teardown only. Calling the logout endpoint here would POST the very access token the
      // server just refused, which is what produced a run of 401s from /auth/logout.
      onUnauthorized: clearSession,
    });
  }, [refreshSession, clearSession]);

  const didBootstrap = useRef(false);

  useEffect(() => {
    if (didBootstrap.current) return;
    didBootstrap.current = true;
    const init = async () => {
      setIsLoading(true);
      try {
        // Attempted unconditionally. There is nothing in localStorage to check first any more, and
        // this single request is what makes one sign-in cover both consoles: opening the admin app
        // after signing in to OneOps, the cookie is already present to exchange. A visitor who is
        // not signed in pays one 401 for it.
        if (!(await refreshSession())) {
          clearSession();
          return;
        }
        try {
          await loadSession();
        } catch {
          clearSession();
        }
      } finally {
        setIsLoading(false);
      }
    };
    void init();
  }, [refreshSession, loadSession, clearSession]);

  const permissions = me?.permissions ?? [];

  const value = useMemo<AuthContextValue>(
    () => ({
      accessToken,
      me,
      profile,
      organization,
      isLoading,
      isAuthenticated: !!me && !!accessToken,
      login,
      loginWithTokens,
      logout,
      refreshSession,
      switchOrg,
      permissions,
      userId: me?.userId ?? null,
      organizationId: me?.organizationId ?? null,
    }),
    [
      accessToken,
      me,
      profile,
      organization,
      isLoading,
      login,
      loginWithTokens,
      logout,
      refreshSession,
      switchOrg,
      permissions,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}

export function useOrganizations() {
  return useQuery({
    queryKey: ["organizations"],
    queryFn: () => apiRequest("/organizations", arraySchema(organizationViewSchema)),
  });
}
