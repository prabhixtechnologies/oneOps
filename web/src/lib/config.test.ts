import { afterEach, describe, expect, it, vi } from "vitest";

async function loadConfig(apiUrl?: string) {
  vi.resetModules();
  if (apiUrl === undefined) {
    vi.stubEnv("VITE_API_URL", "");
  } else {
    vi.stubEnv("VITE_API_URL", apiUrl);
  }
  return import("@/lib/config");
}

describe("API_V1", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.resetModules();
  });

  it("appends the version to a bare origin", async () => {
    const { API_V1 } = await loadConfig("https://api.example.com");
    expect(API_V1).toBe("https://api.example.com/api/v1");
  });

  it("does not double the version when the origin already carries it", async () => {
    // The console was once built this way, and every request went to /api/v1/api/v1/... which the
    // server answered with "Authentication is required" rather than a 404.
    const { API_V1 } = await loadConfig("https://api.example.com/api/v1");
    expect(API_V1).toBe("https://api.example.com/api/v1");
  });

  it("tolerates a trailing slash", async () => {
    const { API_V1 } = await loadConfig("https://api.example.com/");
    expect(API_V1).toBe("https://api.example.com/api/v1");
  });

  it("tolerates a trailing slash after the version", async () => {
    const { API_V1 } = await loadConfig("https://api.example.com/api/v1/");
    expect(API_V1).toBe("https://api.example.com/api/v1");
  });

  it("keeps a port", async () => {
    const { API_V1 } = await loadConfig("http://localhost:8080");
    expect(API_V1).toBe("http://localhost:8080/api/v1");
  });
});
