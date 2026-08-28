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
import { apiRequest, configureApiClient } from "./api-client";
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

const REFRESH_KEY = "prabhix_refresh_token";

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  me: AuthMe | null;
  profile: UserProfile | null;
  organization: OrganizationView | null;
  isLoading: boolean;
  isAuthenticated: boolean;
}

interface AuthContextValue extends AuthState {
  login: (email: string, password: string) => Promise<void>;
  loginWithTokens: (accessToken: string, refreshToken: string) => Promise<void>;
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
  const [refreshToken, setRefreshTokenState] = useState<string | null>(() =>
    localStorage.getItem(REFRESH_KEY),
  );
  const [me, setMe] = useState<AuthMe | null>(null);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [organization, setOrganization] = useState<OrganizationView | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Tokens live in refs as well as state. The refs are what the callbacks below read; the state
  // exists only so the tree re-renders. Reading a token from state inside a callback would change
  // that callback's identity on every rotation, and since one of those callbacks is a dependency
  // of the bootstrap effect, rotating a token would re-trigger the bootstrap and refresh again.
  const accessTokenRef = useRef<string | null>(null);
  const refreshTokenRef = useRef<string | null>(localStorage.getItem(REFRESH_KEY));
  const orgIdRef = useRef<string | null>(null);

  // The refresh token the server last rejected. Without this, every query that 401s starts its
  // own refresh with a token already known to be dead: the single-flight guard below only
  // collapses *concurrent* attempts, so a page with several queries walks through them one at a
  // time and earns a 429 for the trouble.
  const deadRefreshToken = useRef<string | null>(null);

  const setTokens = useCallback((access: string | null, refresh: string | null) => {
    accessTokenRef.current = access;
    refreshTokenRef.current = refresh;
    setAccessTokenState(access);
    setRefreshTokenState(refresh);
    if (refresh) {
      localStorage.setItem(REFRESH_KEY, refresh);
      deadRefreshToken.current = null;
    } else {
      localStorage.removeItem(REFRESH_KEY);
    }
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
    async (access: string, refresh: string) => {
      setTokens(access, refresh);
      await loadSession();
    },
    [loadSession, setTokens],
  );

  /** Drops local credentials without touching the network. */
  const clearSession = useCallback(() => {
    setTokens(null, null);
    setMe(null);
    setProfile(null);
    setOrganization(null);
    orgIdRef.current = null;
    queryClient.clear();
  }, [queryClient, setTokens]);

  const logout = useCallback(async () => {
    try {
      if (accessTokenRef.current) {
        await apiRequest("/auth/logout", { parse: () => undefined }, { method: "POST" });
      }
    } catch {
      // ignore logout errors
    }
    clearSession();
  }, [clearSession]);

  // A refresh token is single-use: the server rotates it and treats a second presentation of the
  // same token as theft, revoking every session the user has. loadSession() fans out three
  // requests at once, so without this guard one expired access token yields three simultaneous
  // 401s, three refreshes spending the same token, and an immediate forced logout.
  const refreshInFlight = useRef<Promise<boolean> | null>(null);

  // Exchanges the stored refresh token for a new pair. Deliberately does not reload the session:
  // doing that inside the single-flight promise meant a 401 from /auth/me would ask for a refresh,
  // be handed back the very promise that was waiting on it, and deadlock — leaving refreshInFlight
  // set forever so no later refresh could run either.
  const refreshSession = useCallback(async (): Promise<boolean> => {
    const existing = refreshInFlight.current;
    if (existing) return existing;

    // localStorage wins over the ref so that a second tab picks up a token rotated by the first
    // instead of replaying the one it captured when it mounted.
    const token = localStorage.getItem(REFRESH_KEY) ?? refreshTokenRef.current;
    if (!token || token === deadRefreshToken.current) return false;

    const attempt = (async () => {
      try {
        const tokens = await apiRequest(
          "/auth/refresh",
          authTokensSchema,
          {
            method: "POST",
            body: { refreshToken: token },
            skipAuth: true,
            skipOrg: true,
          },
        );
        setTokens(tokens.accessToken, tokens.refreshToken);
        return true;
      } catch {
        // Remember the failure so siblings stop presenting the same token. Replaying it reads as
        // theft to the server, which revokes the chain it belongs to.
        deadRefreshToken.current = token;
        return false;
      } finally {
        refreshInFlight.current = null;
      }
    })();

    refreshInFlight.current = attempt;
    return attempt;
  }, [setTokens]);

  const loginWithTokens = useCallback(
    async (access: string, refresh: string) => {
      await applyTokens(access, refresh);
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
      await applyTokens(tokens.accessToken, tokens.refreshToken);
    },
    [applyTokens],
  );

  const switchOrg = useCallback(
    async (orgId: string) => {
      const tokens = await apiRequest(
        `/organizations/${orgId}/select`,
        authTokensSchema,
        { method: "POST" },
      );
      await applyTokens(tokens.accessToken, tokens.refreshToken);
    },
    [applyTokens],
  );

  useEffect(() => {
    configureApiClient({
      getAccessToken: () => accessTokenRef.current,
      getOrgId: () => orgIdRef.current,
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
        const stored = localStorage.getItem(REFRESH_KEY);
        if (!stored) return;
        refreshTokenRef.current = stored;
        if (!(await refreshSession())) {
          // A leftover token from a previous deploy or a revoked session is not an error worth
          // reporting; the visitor simply is not signed in.
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
      refreshToken,
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
      refreshToken,
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
