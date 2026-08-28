import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
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
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [refreshToken, setRefreshToken] = useState<string | null>(() =>
    localStorage.getItem(REFRESH_KEY),
  );
  const [me, setMe] = useState<AuthMe | null>(null);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [organization, setOrganization] = useState<OrganizationView | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const loadSession = useCallback(async () => {
    const authMe = await fetchMe();
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
      setAccessToken(access);
      setRefreshToken(refresh);
      localStorage.setItem(REFRESH_KEY, refresh);
      await loadSession();
    },
    [loadSession],
  );

  const logout = useCallback(async () => {
    try {
      if (accessToken) {
        await apiRequest("/auth/logout", { parse: () => undefined }, { method: "POST" });
      }
    } catch {
      // ignore logout errors
    }
    setAccessToken(null);
    setRefreshToken(null);
    setMe(null);
    setProfile(null);
    setOrganization(null);
    localStorage.removeItem(REFRESH_KEY);
    queryClient.clear();
  }, [accessToken, queryClient]);

  const refreshSession = useCallback(async (): Promise<boolean> => {
    const token = refreshToken ?? localStorage.getItem(REFRESH_KEY);
    if (!token) return false;
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
      setAccessToken(tokens.accessToken);
      setRefreshToken(tokens.refreshToken);
      localStorage.setItem(REFRESH_KEY, tokens.refreshToken);
      await loadSession();
      return true;
    } catch {
      return false;
    }
  }, [refreshToken, loadSession]);

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
      getAccessToken: () => accessToken,
      getOrgId: () => me?.organizationId ?? null,
      refreshTokens: refreshSession,
      onUnauthorized: () => {
        void logout();
      },
    });
  }, [accessToken, me?.organizationId, refreshSession, logout]);

  useEffect(() => {
    const init = async () => {
      setIsLoading(true);
      try {
        const stored = localStorage.getItem(REFRESH_KEY);
        if (stored) {
          setRefreshToken(stored);
          const ok = await refreshSession();
          if (!ok) {
            localStorage.removeItem(REFRESH_KEY);
          }
        }
      } finally {
        setIsLoading(false);
      }
    };
    void init();
  }, [refreshSession]);

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
