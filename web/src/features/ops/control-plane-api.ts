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
  return apiRequest("/oneops/admin/platform/billing/revenue", revenueSnapshotSchema, skipOrg);
}

export async function fetchMobiRevenue(): Promise<RevenueSnapshot> {
  return apiRequest("/oneops/admin/platform/mobistack/billing/revenue", revenueSnapshotSchema, skipOrg);
}

export async function fetchMobiPayments() {
  return apiRequest("/oneops/admin/platform/mobistack/billing/orders", z.array(mobiPaymentSchema), skipOrg);
}

export async function fetchMobiWorkspaces() {
  return apiRequest("/oneops/admin/platform/mobistack/workspaces", z.array(mobiWorkspaceSchema), skipOrg);
}

export async function setMobiWorkspaceActive(id: string, active: boolean, reason?: string) {
  await apiRequestVoid(
    `/oneops/admin/platform/mobistack/workspaces/${active ? "activate" : "suspend"}?id=${id}`,
    { method: "POST", body: reason ? { reason } : {}, ...skipOrg },
  );
}

export async function fetchAwsSummary() {
  return apiRequest("/oneops/admin/platform/aws/summary?range=30d", awsSummarySchema, skipOrg);
}

export async function fetchEc2Instances() {
  return apiRequest("/oneops/admin/platform/aws/instances", ec2ListSchema, skipOrg);
}

export async function fetchRds() {
  return apiRequest("/oneops/admin/platform/aws/rds", rdsListSchema, skipOrg);
}

export async function fetchElastiCache() {
  return apiRequest("/oneops/admin/platform/aws/elasticache", cacheListSchema, skipOrg);
}

export async function fetchEcr() {
  return apiRequest("/oneops/admin/platform/aws/ecr", ecrListSchema, skipOrg);
}

export async function fetchProductHealth() {
  return apiRequest("/oneops/admin/platform/health/products", productHealthSchema, skipOrg);
}

export async function fetchGithubChecks() {
  return apiRequest("/oneops/admin/platform/github/checks", githubChecksSchema, skipOrg);
}

export async function fetchGithubPulls() {
  return apiRequest("/oneops/admin/platform/github/pulls", githubPullsSchema, skipOrg);
}

export async function fetchGithubDeploys() {
  return apiRequest("/oneops/admin/platform/github/deploys", githubDeploysSchema, skipOrg);
}

export async function promoteRelease(service: string, tag: string) {
  return apiRequest("/oneops/admin/platform/github/promote", promoteResponseSchema, {
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
  return apiRequest(`/oneops/admin/platform/pnl?${params}`, pnlSchema, skipOrg);
}

export async function fetchMailHealth() {
  return apiRequest("/oneops/admin/platform/mail/health", mailHealthSchema, skipOrg);
}

export async function fetchStaffMe() {
  return apiRequest("/oneops/admin/platform/staff/me", staffMeSchema, skipOrg);
}

export async function fetchStaffGrants() {
  return apiRequest("/oneops/admin/platform/staff", z.array(staffGrantSchema), skipOrg);
}

export async function grantStaffRole(userId: string, role: string, note?: string) {
  return apiRequest("/oneops/admin/platform/staff/grants", staffGrantSchema, {
    method: "POST",
    body: { userId, role, note },
    ...skipOrg,
  });
}

export async function revokeStaffRole(userId: string, role: string, note?: string) {
  await apiRequestVoid("/oneops/admin/platform/staff/revocations", {
    method: "POST",
    body: { userId, role, note },
    ...skipOrg,
  });
}

export async function breakGlassRevoke(userId: string, reason: string) {
  return apiRequest(
    `/oneops/admin/platform/staff/break-glass/users/revoke-tokens?userId=${userId}`,
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
  return apiRequest(`/oneops/admin/platform/identity/users${qs ? `?${qs}` : ""}`, identityPageSchema, skipOrg);
}

export async function fetchIdentityUser(id: string) {
  return apiRequest(`/oneops/admin/platform/identity/users?id=${id}`, identityUserDetailSchema, skipOrg);
}

export async function identityUserAction(
  id: string,
  action: "disable" | "enable" | "unlock" | "force-reset" | "revoke-sessions",
  reason?: string,
) {
  await apiRequestVoid(`/oneops/admin/platform/identity/users/${action}?id=${id}`, {
    method: "POST",
    body: reason ? { reason } : {},
    ...skipOrg,
  });
}

export async function fetchIdentityClients() {
  return apiRequest("/oneops/admin/platform/identity/clients", jsonRecordsSchema, skipOrg);
}

export async function fetchIdentityKeys() {
  return apiRequest("/oneops/admin/platform/identity/keys", jsonRecordsSchema, skipOrg);
}

export async function fetchIdentityEvents(query?: { userId?: string; type?: string }) {
  const params = new URLSearchParams({ limit: "50" });
  if (query?.userId) params.set("userId", query.userId);
  if (query?.type) params.set("type", query.type);
  return apiRequest(`/oneops/admin/platform/identity/events?${params}`, identityPageSchema, skipOrg);
}

export async function fetchMobiLive() {
  return apiRequest("/oneops/admin/platform/mobistack/live", jsonRecordsSchema, skipOrg);
}

export async function kickMobiUser(userId: string, deviceId?: string, reason?: string) {
  return apiRequest(`/oneops/admin/platform/mobistack/live/kick?userId=${userId}`, jsonUnknownSchema, {
    method: "POST",
    body: { deviceId, reason },
    ...skipOrg,
  });
}

export async function fetchMobiFlags() {
  return apiRequest("/oneops/admin/platform/mobistack/feature-flags", jsonRecordsSchema, skipOrg);
}

export async function upsertMobiFlag(code: string, enabled: boolean) {
  return apiRequest("/oneops/admin/platform/mobistack/feature-flags", jsonUnknownSchema, {
    method: "PUT",
    body: { code, enabled },
    ...skipOrg,
  });
}

export async function fetchMobiReleases() {
  return apiRequest("/oneops/admin/platform/mobistack/app-releases", jsonRecordsSchema, skipOrg);
}

export async function updateMobiRelease(platform: string, body: Record<string, unknown>) {
  return apiRequest(`/oneops/admin/platform/mobistack/app-releases?platform=${platform}`, jsonUnknownSchema, {
    method: "PUT",
    body,
    ...skipOrg,
  });
}

export async function fetchMobiSupport(status?: string) {
  const qs = status ? `?status=${encodeURIComponent(status)}` : "";
  return apiRequest(`/oneops/admin/platform/mobistack/support${qs}`, jsonRecordsSchema, skipOrg);
}

export async function replyMobiSupport(id: string, message: string) {
  return apiRequest(`/oneops/admin/platform/mobistack/support/messages?id=${id}`, jsonUnknownSchema, {
    method: "POST",
    body: { message },
    ...skipOrg,
  });
}

export async function resolveMobiSupport(id: string) {
  return apiRequest(`/oneops/admin/platform/mobistack/support/resolve?id=${id}`, jsonUnknownSchema, {
    method: "POST",
    body: {},
    ...skipOrg,
  });
}

export async function fetchMobiPlans() {
  return apiRequest("/oneops/admin/platform/mobistack/plans", jsonRecordsSchema, skipOrg);
}

export async function fetchCommonsQueue() {
  return apiRequest("/oneops/admin/platform/commons/queue", jsonUnknownSchema, skipOrg);
}

export async function reviewCommons(id: string, decision: "accept" | "reject", note?: string) {
  return apiRequest(`/oneops/admin/platform/commons/${decision}?id=${id}`, jsonUnknownSchema, {
    method: "POST",
    body: note ? { note, reason: note } : {},
    ...skipOrg,
  });
}
