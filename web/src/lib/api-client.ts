import { ZodError } from "zod";
import { API_V1 } from "./config";
import { apiErrorSchema, type ApiError } from "./schemas/common";

export class ApiClientError extends Error {
  readonly code: string;
  readonly fieldErrors?: Record<string, string>;
  readonly status: number;

  constructor(status: number, error: ApiError) {
    super(error.message);
    this.name = "ApiClientError";
    this.status = status;
    this.code = error.code;
    this.fieldErrors = error.fieldErrors;
  }
}

export interface RequestOptions {
  method?: string;
  body?: unknown;
  headers?: Record<string, string>;
  signal?: AbortSignal;
  skipAuth?: boolean;
  skipOrg?: boolean;
}

export type TokenProvider = () => string | null;
export type OrgProvider = () => string | null;
export type RefreshHandler = () => Promise<boolean>;
export type LogoutHandler = () => void;

let getAccessToken: TokenProvider = () => null;
let getOrgId: OrgProvider = () => null;
let refreshTokens: RefreshHandler = async () => false;
let onUnauthorized: LogoutHandler = () => undefined;

export function configureApiClient(config: {
  getAccessToken: TokenProvider;
  getOrgId: OrgProvider;
  refreshTokens: RefreshHandler;
  onUnauthorized: LogoutHandler;
}) {
  getAccessToken = config.getAccessToken;
  getOrgId = config.getOrgId;
  refreshTokens = config.refreshTokens;
  onUnauthorized = config.onUnauthorized;
}

function parseSchema<T>(
  schema: { parse: (data: unknown) => T },
  data: unknown,
  endpoint: string,
): T {
  try {
    return schema.parse(data);
  } catch (err) {
    if (err instanceof ZodError) {
      const fields = err.errors.map((e) => e.path.join(".") || "root").join(", ");
      // Pages render a generic "failed to load" for this, which makes a rejected-but-valid response
      // look exactly like an outage: the request is a 200 in the server log and the network tab, yet
      // the screen is an error with a retry button that can never succeed. The dashboard died this
      // way over one absent key. Naming the endpoint and the offending paths costs nothing and turns
      // an afternoon of guessing into a glance at the console.
      console.error(
        `[api] ${endpoint} returned a response that does not match its schema:`,
        err.errors.map((e) => `${e.path.join(".") || "root"}: ${e.message}`),
      );
      throw new ApiClientError(200, {
        code: "SCHEMA_MISMATCH",
        message: `Unexpected API response shape (${fields})`,
      });
    }
    throw err;
  }
}

async function parseError(response: Response): Promise<ApiClientError> {
  try {
    const json: unknown = await response.json();
    const parsed = apiErrorSchema.safeParse(json);
    if (parsed.success) {
      return new ApiClientError(response.status, parsed.data);
    }
  } catch {
    // fall through
  }
  return new ApiClientError(response.status, {
    code: "UNKNOWN",
    message: response.statusText || "Request failed",
  });
}

function buildHeaders(options: RequestOptions, jsonBody: boolean): Record<string, string> {
  const headers: Record<string, string> = {
    Accept: "application/json",
    ...options.headers,
  };

  if (jsonBody) {
    headers["Content-Type"] = "application/json";
  }

  if (!options.skipAuth) {
    const token = getAccessToken();
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  if (!options.skipOrg) {
    const orgId = getOrgId();
    if (orgId) headers["X-Prabhix-Org"] = orgId;
  }

  return headers;
}

export async function apiRequest<T>(
  path: string,
  schema: { parse: (data: unknown) => T },
  options: RequestOptions = {},
): Promise<T> {
  const url = path.startsWith("http") ? path : `${API_V1}${path}`;

  const execute = async (retried: boolean): Promise<T> => {
    const hasJsonBody = options.body !== undefined;
    const headers = buildHeaders(options, hasJsonBody);

    const response = await fetch(url, {
      method: options.method ?? (hasJsonBody ? "POST" : "GET"),
      headers,
      body: hasJsonBody ? JSON.stringify(options.body) : undefined,
      signal: options.signal,
      credentials: "include",
    });

    if (response.status === 401 && !retried && !options.skipAuth) {
      const refreshed = await refreshTokens();
      if (refreshed) return execute(true);
      onUnauthorized();
      throw new ApiClientError(401, { code: "UNAUTHORIZED", message: "Session expired" });
    }

    if (!response.ok) {
      throw await parseError(response);
    }

    if (response.status === 204 || response.headers.get("content-length") === "0") {
      return parseSchema(schema, null, path);
    }

    const contentType = response.headers.get("content-type");
    if (!contentType?.includes("application/json")) {
      return parseSchema(schema, null, path);
    }

    const json: unknown = await response.json();
    return parseSchema(schema, json, path);
  };

  return execute(false);
}

export async function apiRequestVoid(
  path: string,
  options: RequestOptions = {},
): Promise<void> {
  await apiRequest(path, { parse: () => undefined }, options);
}

export async function apiUpload<T>(
  path: string,
  file: File,
  schema: { parse: (data: unknown) => T },
  options: { purpose?: string; signal?: AbortSignal } = {},
): Promise<T> {
  const params = new URLSearchParams();
  if (options.purpose) params.set("purpose", options.purpose);
  const qs = params.toString();
  const url = `${API_V1}${path}${qs ? `?${qs}` : ""}`;

  const execute = async (retried: boolean): Promise<T> => {
    const formData = new FormData();
    formData.append("file", file);

    const headers = buildHeaders({}, false);

    const response = await fetch(url, {
      method: "POST",
      headers,
      body: formData,
      signal: options.signal,
      credentials: "include",
    });

    if (response.status === 401 && !retried) {
      const refreshed = await refreshTokens();
      if (refreshed) return execute(true);
      onUnauthorized();
      throw new ApiClientError(401, { code: "UNAUTHORIZED", message: "Session expired" });
    }

    if (!response.ok) {
      throw await parseError(response);
    }

    const json: unknown = await response.json();
    return parseSchema(schema, json, path);
  };

  return execute(false);
}

export async function apiDownload(path: string): Promise<{ blob: Blob; filename: string }> {
  const url = `${API_V1}${path}`;

  const execute = async (retried: boolean): Promise<{ blob: Blob; filename: string }> => {
    const headers = buildHeaders({}, false);

    const response = await fetch(url, { headers, credentials: "include" });

    if (response.status === 401 && !retried) {
      const refreshed = await refreshTokens();
      if (refreshed) return execute(true);
      onUnauthorized();
      throw new ApiClientError(401, { code: "UNAUTHORIZED", message: "Session expired" });
    }

    if (!response.ok) {
      throw await parseError(response);
    }

    const blob = await response.blob();
    const disposition = response.headers.get("content-disposition") ?? "";
    const match = /filename="?([^";\n]+)"?/.exec(disposition);
    const filename = match?.[1] ?? "download";
    return { blob, filename };
  };

  return execute(false);
}

export function triggerBlobDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

export function getApiErrorMessage(err: unknown): string {
  if (err instanceof ApiClientError) return err.message;
  if (err instanceof Error) return err.message;
  return "Something went wrong";
}
