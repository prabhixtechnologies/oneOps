import { apiRequest } from "@/lib/api-client";
import {
  awsSummarySchema,
  ec2ListSchema,
  githubChecksSchema,
  mobiPaymentSchema,
  mobiWorkspaceSchema,
  pnlSchema,
  productHealthSchema,
  revenueSnapshotSchema,
  type RevenueSnapshot,
} from "@/lib/schemas/control-plane";
import { z } from "zod";
import { getAccessToken } from "@/lib/auth-token-bridge";

const MOBISTACK_API =
  (import.meta.env.VITE_MOBISTACK_API_URL as string | undefined)?.replace(/\/+$/, "") ??
  "https://mobistack.prabhixtechnologies.com/api/v1";

async function fetchMobiJson<T>(
  path: string,
  schema: { parse: (data: unknown) => T },
  init?: RequestInit,
): Promise<T> {
  const token = getAccessToken();
  const headers: Record<string, string> = {
    Accept: "application/json",
    ...(init?.headers as Record<string, string> | undefined),
  };
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(`${MOBISTACK_API}${path}`, { ...init, headers });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`${res.status} ${path}: ${text.slice(0, 200)}`);
  }
  return schema.parse(await res.json());
}

export async function fetchOneOpsRevenue(): Promise<RevenueSnapshot> {
  return apiRequest("/admin/platform/billing/revenue", revenueSnapshotSchema, {
    skipOrg: true,
  });
}

export async function fetchMobiRevenue(): Promise<RevenueSnapshot> {
  return fetchMobiJson("/admin/billing/revenue", revenueSnapshotSchema);
}

export async function fetchMobiPayments() {
  return fetchMobiJson("/admin/billing/orders", z.array(mobiPaymentSchema));
}

export async function fetchMobiWorkspaces() {
  return fetchMobiJson("/admin/workspaces", z.array(mobiWorkspaceSchema));
}

export async function setMobiWorkspaceActive(id: string, active: boolean) {
  const token = getAccessToken();
  const res = await fetch(
    `${MOBISTACK_API}/admin/workspaces/${id}/${active ? "activate" : "suspend"}`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
    },
  );
  if (!res.ok) throw new Error(`Failed to ${active ? "activate" : "suspend"} shop`);
}

export async function fetchAwsSummary() {
  return apiRequest("/admin/platform/aws/summary?range=30d", awsSummarySchema, {
    skipOrg: true,
  });
}

export async function fetchEc2Instances() {
  return apiRequest("/admin/platform/aws/instances", ec2ListSchema, { skipOrg: true });
}

export async function fetchProductHealth() {
  return apiRequest("/admin/platform/health/products", productHealthSchema, {
    skipOrg: true,
  });
}

export async function fetchGithubChecks() {
  return apiRequest("/admin/platform/github/checks", githubChecksSchema, {
    skipOrg: true,
  });
}

export async function fetchPnL(mobiCaptured: number, oneopsCaptured: number, awsMtd?: number) {
  const params = new URLSearchParams({
    mobiCaptured: String(mobiCaptured),
    oneopsCaptured: String(oneopsCaptured),
  });
  if (awsMtd != null) params.set("awsMtd", String(awsMtd));
  return apiRequest(`/admin/platform/pnl?${params}`, pnlSchema, { skipOrg: true });
}
