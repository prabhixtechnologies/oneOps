import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const apiRequest = vi.fn();
let clientConfig: {
  refreshTokens: () => Promise<boolean>;
  onUnauthorized: () => void;
} | null = null;

vi.mock("@/lib/api-client", () => ({
  apiRequest: (...args: unknown[]) => apiRequest(...args),
  configureApiClient: (config: typeof clientConfig) => {
    clientConfig = config;
  },
}));

const REFRESH_KEY = "prabhix_refresh_token";

/** Paths passed to apiRequest, in order. */
function requestedPaths(): string[] {
  return apiRequest.mock.calls.map((call) => call[0] as string);
}

async function renderProvider() {
  const { AuthProvider, useAuth } = await import("@/lib/auth");

  let snapshot: { isLoading: boolean; isAuthenticated: boolean } = {
    isLoading: true,
    isAuthenticated: false,
  };

  function Probe() {
    const { isLoading, isAuthenticated } = useAuth();
    snapshot = { isLoading, isAuthenticated };
    return null;
  }

  render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider>
        <Probe />
      </AuthProvider>
    </QueryClientProvider>,
  );

  await waitFor(() => expect(snapshot.isLoading).toBe(false));
  return () => snapshot;
}

describe("AuthProvider with a refresh token the server rejects", () => {
  beforeEach(() => {
    apiRequest.mockReset();
    clientConfig = null;
    localStorage.clear();
    vi.resetModules();
  });

  it("signs the visitor out quietly rather than reporting a failure", async () => {
    localStorage.setItem(REFRESH_KEY, "stale-token");
    apiRequest.mockRejectedValue(new Error("401"));

    const read = await renderProvider();

    expect(read().isAuthenticated).toBe(false);
    expect(localStorage.getItem(REFRESH_KEY)).toBeNull();
  });

  it("never presents the dead token to the logout endpoint", async () => {
    localStorage.setItem(REFRESH_KEY, "stale-token");
    apiRequest.mockRejectedValue(new Error("401"));

    await renderProvider();

    // Calling /auth/logout with an access token the server has already refused produced a run of
    // 401s in the browser console and told the user nothing.
    expect(requestedPaths()).not.toContain("/auth/logout");
  });

  it("stops re-presenting a token already refused, so siblings cannot earn a rate limit", async () => {
    localStorage.setItem(REFRESH_KEY, "stale-token");
    apiRequest.mockRejectedValue(new Error("401"));

    await renderProvider();
    expect(requestedPaths().filter((p) => p === "/auth/refresh")).toHaveLength(1);

    // Bootstrap clears the token on failure, and even if a stale copy is put back the value is
    // remembered as dead: replaying it reads as theft to the server and revokes the chain.
    localStorage.setItem(REFRESH_KEY, "stale-token");
    await clientConfig?.refreshTokens();
    await clientConfig?.refreshTokens();

    expect(requestedPaths().filter((p) => p === "/auth/refresh")).toHaveLength(1);
  });

  it("does not call the API at all when there was never a stored token", async () => {
    const read = await renderProvider();

    expect(read().isAuthenticated).toBe(false);
    expect(apiRequest).not.toHaveBeenCalled();
  });
});

describe("AuthProvider when the refresh succeeds", () => {
  beforeEach(() => {
    apiRequest.mockReset();
    clientConfig = null;
    localStorage.clear();
    vi.resetModules();
  });

  it("exchanges the token once and then loads the session", async () => {
    localStorage.setItem(REFRESH_KEY, "good-token");
    apiRequest.mockImplementation((path: string) => {
      if (path === "/auth/refresh") {
        return Promise.resolve({ accessToken: "access-1", refreshToken: "rotated-1" });
      }
      if (path === "/auth/me") {
        return Promise.resolve({
          userId: "u1",
          email: "a@example.com",
          displayName: "A",
          organizationId: "o1",
          sessionId: "s1",
          permissions: [],
          platformAdmin: false,
        });
      }
      return Promise.resolve(null);
    });

    const read = await renderProvider();

    expect(read().isAuthenticated).toBe(true);
    expect(localStorage.getItem(REFRESH_KEY)).toBe("rotated-1");
    expect(requestedPaths().filter((p) => p === "/auth/refresh")).toHaveLength(1);
    expect(requestedPaths()).toContain("/auth/me");
  });
});
