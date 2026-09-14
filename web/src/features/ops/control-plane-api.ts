import { apiRequest, apiRequestVoid } from "@/lib/api-client";
import {
  awsSummarySchema,
  cacheListSchema,
  ecrListSchema,
  ec2ListSchema,
  githubChecksSchema,
  githubDeploysSchema,
  githubPullsSchema,
  identityPageSchema,
  identityUserDetailSchema,
  jsonRecordsSchema,
  jsonUnknownSchema,
  mailHealthSchema,
  mobiPaymentSchema,
  mobiWorkspaceSchema,
  pnlSchema,
  productHealthSchema,
  promoteResponseSchema,
  rdsListSchema,
  revenueSnapshotSchema,
  staffGrantSchema,
  staffMeSchema,
  type RevenueSnapshot,
} from "@/lib/schemas/control-plane";
import { z } from "zod";

const skipOrg = { skipOrg: true as const };

export async function fetchOneOpsRevenue(): Promise<RevenueSnapshot> {
  return apiRequest("/admin/platform/billing/revenue", revenueSnapshotSchema, skipOrg);
}

export async function fetchMobiRevenue(): Promise<RevenueSnapshot> {
  return apiRequest("/admin/platform/mobistack/billing/revenue", revenueSnapshotSchema, skipOrg);
}

export async function fetchMobiPayments() {
  return apiRequest("/admin/platform/mobistack/billing/orders", z.array(mobiPaymentSchema), skipOrg);
}

export async function fetchMobiWorkspaces() {
  return apiRequest("/admin/platform/mobistack/workspaces", z.array(mobiWorkspaceSchema), skipOrg);
}

export async function setMobiWorkspaceActive(id: string, active: boolean, reason?: string) {
  await apiRequestVoid(
    `/admin/platform/mobistack/workspaces/${id}/${active ? "activate" : "suspend"}`,
    { method: "POST", body: reason ? { reason } : {}, ...skipOrg },
  );
}

export async function fetchAwsSummary() {
  return apiRequest("/admin/platform/aws/summary?range=30d", awsSummarySchema, skipOrg);
}

export async function fetchEc2Instances() {
  return apiRequest("/admin/platform/aws/instances", ec2ListSchema, skipOrg);
}

export async function fetchRds() {
  return apiRequest("/admin/platform/aws/rds", rdsListSchema, skipOrg);
}

export async function fetchElastiCache() {
  return apiRequest("/admin/platform/aws/elasticache", cacheListSchema, skipOrg);
}

export async function fetchEcr() {
  return apiRequest("/admin/platform/aws/ecr", ecrListSchema, skipOrg);
}

export async function fetchProductHealth() {
  return apiRequest("/admin/platform/health/products", productHealthSchema, skipOrg);
}

export async function fetchGithubChecks() {
  return apiRequest("/admin/platform/github/checks", githubChecksSchema, skipOrg);
}

export async function fetchGithubPulls() {
  return apiRequest("/admin/platform/github/pulls", githubPullsSchema, skipOrg);
}

export async function fetchGithubDeploys() {
  return apiRequest("/admin/platform/github/deploys", githubDeploysSchema, skipOrg);
}

export async function promoteRelease(service: string, tag: string) {
  return apiRequest("/admin/platform/github/promote", promoteResponseSchema, {
    method: "POST",
    body: { service, tag },
    ...skipOrg,
  });
}

export async function fetchPnL(mobiCaptured: number, oneopsCaptured: number, awsMtd?: number) {
  const params = new URLSearchParams({
    mobiCaptured: String(mobiCaptured),
    oneopsCaptured: String(oneopsCaptured),
  });
  if (awsMtd != null) params.set("awsMtd", String(awsMtd));
  return apiRequest(`/admin/platform/pnl?${params}`, pnlSchema, skipOrg);
}

export async function fetchMailHealth() {
  return apiRequest("/admin/platform/mail/health", mailHealthSchema, skipOrg);
}

export async function fetchStaffMe() {
  return apiRequest("/admin/platform/staff/me", staffMeSchema, skipOrg);
}

export async function fetchStaffGrants() {
  return apiRequest("/admin/platform/staff", z.array(staffGrantSchema), skipOrg);
}

export async function grantStaffRole(userId: string, role: string, note?: string) {
  return apiRequest("/admin/platform/staff/grants", staffGrantSchema, {
    method: "POST",
    body: { userId, role, note },
    ...skipOrg,
  });
}

export async function revokeStaffRole(userId: string, role: string, note?: string) {
  await apiRequestVoid("/admin/platform/staff/revocations", {
    method: "POST",
    body: { userId, role, note },
    ...skipOrg,
  });
}

export async function breakGlassRevoke(userId: string, reason: string) {
  return apiRequest(
    `/admin/platform/staff/break-glass/users/${userId}/revoke-tokens`,
    jsonUnknownSchema,
    { method: "POST", body: { reason }, ...skipOrg },
  );
}

export async function searchIdentityUsers(query?: { q?: string; status?: string; cursor?: string }) {
  const params = new URLSearchParams();
  if (query?.q) params.set("q", query.q);
  if (query?.status) params.set("status", query.status);
  if (query?.cursor) params.set("cursor", query.cursor);
  params.set("limit", "50");
  const qs = params.toString();
  return apiRequest(`/admin/platform/identity/users${qs ? `?${qs}` : ""}`, identityPageSchema, skipOrg);
}

export async function fetchIdentityUser(id: string) {
  return apiRequest(`/admin/platform/identity/users/${id}`, identityUserDetailSchema, skipOrg);
}

export async function identityUserAction(
  id: string,
  action: "disable" | "enable" | "unlock" | "force-reset" | "revoke-sessions",
  reason?: string,
) {
  await apiRequestVoid(`/admin/platform/identity/users/${id}/${action}`, {
    method: "POST",
    body: reason ? { reason } : {},
    ...skipOrg,
  });
}

export async function fetchIdentityClients() {
  return apiRequest("/admin/platform/identity/clients", jsonRecordsSchema, skipOrg);
}

export async function fetchIdentityKeys() {
  return apiRequest("/admin/platform/identity/keys", jsonRecordsSchema, skipOrg);
}

export async function fetchIdentityEvents(query?: { userId?: string; type?: string }) {
  const params = new URLSearchParams({ limit: "50" });
  if (query?.userId) params.set("userId", query.userId);
  if (query?.type) params.set("type", query.type);
  return apiRequest(`/admin/platform/identity/events?${params}`, identityPageSchema, skipOrg);
}

export async function fetchMobiLive() {
  return apiRequest("/admin/platform/mobistack/live", jsonRecordsSchema, skipOrg);
}

export async function kickMobiUser(userId: string, deviceId?: string, reason?: string) {
  return apiRequest(`/admin/platform/mobistack/live/${userId}/kick`, jsonUnknownSchema, {
    method: "POST",
    body: { deviceId, reason },
    ...skipOrg,
  });
}

export async function fetchMobiFlags() {
  return apiRequest("/admin/platform/mobistack/feature-flags", jsonRecordsSchema, skipOrg);
}

export async function upsertMobiFlag(code: string, enabled: boolean) {
  return apiRequest("/admin/platform/mobistack/feature-flags", jsonUnknownSchema, {
    method: "PUT",
    body: { code, enabled },
    ...skipOrg,
  });
}

export async function fetchMobiReleases() {
  return apiRequest("/admin/platform/mobistack/app-releases", jsonRecordsSchema, skipOrg);
}

export async function updateMobiRelease(platform: string, body: Record<string, unknown>) {
  return apiRequest(`/admin/platform/mobistack/app-releases/${platform}`, jsonUnknownSchema, {
    method: "PUT",
    body,
    ...skipOrg,
  });
}

export async function fetchMobiSupport(status?: string) {
  const qs = status ? `?status=${encodeURIComponent(status)}` : "";
  return apiRequest(`/admin/platform/mobistack/support${qs}`, jsonRecordsSchema, skipOrg);
}

export async function replyMobiSupport(id: string, message: string) {
  return apiRequest(`/admin/platform/mobistack/support/${id}/messages`, jsonUnknownSchema, {
    method: "POST",
    body: { message },
    ...skipOrg,
  });
}

export async function resolveMobiSupport(id: string) {
  return apiRequest(`/admin/platform/mobistack/support/${id}/resolve`, jsonUnknownSchema, {
    method: "POST",
    body: {},
    ...skipOrg,
  });
}

export async function fetchMobiPlans() {
  return apiRequest("/admin/platform/mobistack/plans", jsonRecordsSchema, skipOrg);
}

export async function fetchCommonsQueue() {
  return apiRequest("/admin/platform/commons/queue", jsonUnknownSchema, skipOrg);
}

export async function reviewCommons(id: string, decision: "accept" | "reject", note?: string) {
  return apiRequest(`/admin/platform/commons/${id}/${decision}`, jsonUnknownSchema, {
    method: "POST",
    body: note ? { note, reason: note } : {},
    ...skipOrg,
  });
}
