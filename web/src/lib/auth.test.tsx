import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const apiRequest = vi.fn();
let clientConfig: {
  refreshTokens: () => Promise<boolean>;
  onUnauthorized: () => void;
} | null = null;

class FakeApiClientError extends Error {
  constructor(readonly status: number) {
    super(`status ${status}`);
  }
}

vi.mock("@/lib/api-client", () => ({
  apiRequest: (...args: unknown[]) => apiRequest(...args),
  ApiClientError: FakeApiClientError,
  configureApiClient: (config: typeof clientConfig) => {
    clientConfig = config;
  },
}));

const SESSION_TOKEN_PATH = "/auth/session/token";

/** Paths passed to apiRequest, in order. */
function requestedPaths(): string[] {
  return apiRequest.mock.calls.map((call) => call[0] as string);
}

function countOf(path: string): number {
  return requestedPaths().filter((p) => p === path).length;
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

const AUTH_ME = {
  userId: "u1",
  email: "a@example.com",
  displayName: "A",
  organizationId: "o1",
  sessionId: "s1",
  permissions: [],
  platformAdmin: false,
};

beforeEach(() => {
  apiRequest.mockReset();
  clientConfig = null;
  localStorage.clear();
  vi.resetModules();
});

describe("AuthProvider when the browser has no usable session", () => {
  it("signs the visitor out quietly rather than reporting a failure", async () => {
    apiRequest.mockRejectedValue(new FakeApiClientError(401));

    const read = await renderProvider();

    expect(read().isAuthenticated).toBe(false);
  });

  it("never presents a refused token to the logout endpoint", async () => {
    apiRequest.mockRejectedValue(new FakeApiClientError(401));

    await renderProvider();

    // Calling /auth/logout with an access token the server has already refused produced a run of
    // 401s in the browser console and told the user nothing.
    expect(requestedPaths()).not.toContain("/auth/logout");
  });

  it("stops retrying once the server has said there is no session", async () => {
    apiRequest.mockRejectedValue(new FakeApiClientError(401));

    await renderProvider();
    expect(countOf(SESSION_TOKEN_PATH)).toBe(1);

    // Several queries 401ing at once each ask for a renewal. The single-flight guard only collapses
    // concurrent attempts, so without remembering the refusal these arrive one at a time and earn a
    // rate limit for a session that does not exist.
    await clientConfig?.refreshTokens();
    await clientConfig?.refreshTokens();

    expect(countOf(SESSION_TOKEN_PATH)).toBe(1);
  });

  it("keeps trying after a failure that was not a refusal", async () => {
    // A 500 or a dropped connection says nothing about whether a session exists. Treating it as
    // "signed out" would strand a signed-in person on the login page until they reloaded.
    apiRequest.mockRejectedValue(new Error("network down"));

    await renderProvider();
    expect(countOf(SESSION_TOKEN_PATH)).toBe(1);

    await clientConfig?.refreshTokens();

    expect(countOf(SESSION_TOKEN_PATH)).toBe(2);
  });
});

describe("AuthProvider when the session cookie is good", () => {
  beforeEach(() => {
    apiRequest.mockImplementation((path: string) => {
      if (path === SESSION_TOKEN_PATH) {
        // No refreshToken in the response, and none needed: this is the shape the server returns
        // for a cookie exchange.
        return Promise.resolve({ accessToken: "access-1", expiresInSeconds: 900 });
      }
      if (path === "/auth/me") return Promise.resolve(AUTH_ME);
      return Promise.resolve(null);
    });
  });

  it("exchanges the cookie once and then loads the session", async () => {
    const read = await renderProvider();

    expect(read().isAuthenticated).toBe(true);
    expect(countOf(SESSION_TOKEN_PATH)).toBe(1);
    expect(requestedPaths()).toContain("/auth/me");
  });

  it("stores no session credential in localStorage", async () => {
    await renderProvider();

    // The point of the cookie: the credential that outlives the page is HttpOnly and unreachable
    // from script. A refresh token used to sit here, readable by anything injected onto the page.
    expect(Object.keys(localStorage)).toHaveLength(0);
  });

  it("collapses concurrent renewals into a single request", async () => {
    await renderProvider();
    const before = countOf(SESSION_TOKEN_PATH);

    await Promise.all([
      clientConfig?.refreshTokens(),
      clientConfig?.refreshTokens(),
      clientConfig?.refreshTokens(),
    ]);

    expect(countOf(SESSION_TOKEN_PATH)).toBe(before + 1);
  });
});
