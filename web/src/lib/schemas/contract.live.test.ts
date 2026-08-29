/**
 * Contract check: every console endpoint, fetched for real and parsed with the schema the app uses.
 *
 * This is opt-in and skips unless PBX_CONTRACT_EMAIL and PBX_CONTRACT_PASSWORD are set, because it
 * needs a real account and a running API:
 *
 *   PBX_CONTRACT_EMAIL=you@example.com PBX_CONTRACT_PASSWORD=... \
 *     PBX_CONTRACT_API=https://api.example.com npx vitest run contract.live
 *
 * It exists because a mismatch between a response and its schema is invisible until someone opens
 * the page: the request is a 200 everywhere you would look, and the only symptom is a generic error
 * with a retry button. Six pages were broken this way at once — the dashboard, live chat, feature
 * flags, billing and two settings panels — each on a field that is null in ordinary use. Unit tests
 * cannot catch this class of bug because they assert against fixtures written from the same wrong
 * assumption as the schema.
 */
import { describe, expect, it, beforeAll } from "vitest";
import type { ZodTypeAny } from "zod";
import {
  aiAvailabilitySchema,
  aiOrgSettingsSchema,
  aiPromptListSchema,
  aiUsagePageSchema,
  aiUsageSummarySchema,
} from "./ai";
import {
  billingAddressSchema,
  dashboardSchema,
  entitlementsSchema,
  invoiceListPageSchema,
  paymentMethodListSchema,
  planPageSchema,
  subscriptionSchema,
} from "./billing";
import {
  chatCannedReplyListSchema,
  chatInboxCountsSchema,
  chatSettingsSchema,
  conversationListPageSchema,
} from "./chat";
import {
  customerListPageSchema,
  dashboardViewSchema,
  discountListSchema,
  orderListPageSchema,
  productListPageSchema,
  settingsViewSchema,
} from "./commerce";
import { organizationViewSchema, userProfileSchema } from "./common";
import { eventLogPageSchema } from "./logs";
import {
  cannedReplyListSchema,
  domainListSchema,
  effectiveFlagsSchema,
  mailTagListSchema,
  mailboxListSchema,
  templateListSchema,
  threadListPageSchema,
} from "./mail";
import {
  applicationPageSchema,
  leadPageSchema,
  platformOverviewSchema,
  subscriberPageSchema,
  tenantPageSchema,
} from "./ops";
import {
  apiKeyPageSchema,
  auditLogPageSchema,
  invitePageSchema,
  memberListPageSchema,
  rolePageSchema,
  sessionListSchema,
  teamPageSchema,
} from "./org";
import {
  liveVisitorListSchema,
  pageViewListPageSchema,
  visitorAnalyticsSummarySchema,
  visitorListPageSchema,
} from "./visitor";

const API = `${process.env.PBX_CONTRACT_API ?? "https://api.prabhixtechnologies.com"}/api/v1`;
const ORIGIN = process.env.PBX_CONTRACT_ORIGIN ?? "https://oneops.prabhixtechnologies.com";
const EMAIL = process.env.PBX_CONTRACT_EMAIL ?? "";
const PASSWORD = process.env.PBX_CONTRACT_PASSWORD ?? "";
const enabled = EMAIL !== "" && PASSWORD !== "";

let token = "";
let orgId = "";

beforeAll(async () => {
  if (!enabled) return;

  const res = await fetch(`${API}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Origin: ORIGIN },
    body: JSON.stringify({ email: EMAIL, password: PASSWORD }),
  });
  const body = (await res.json()) as { accessToken: string };
  token = body.accessToken;

  const me = await fetch(`${API}/auth/me`, {
    headers: { Authorization: `Bearer ${token}`, Origin: ORIGIN },
  });
  const meBody = (await me.json()) as { organizationId?: string; memberships?: { orgId: string }[] };
  orgId = meBody.organizationId ?? meBody.memberships?.[0]?.orgId ?? "";
}, 60_000);

async function check(path: string, schema: ZodTypeAny) {
  const res = await fetch(`${API}${path}`, {
    headers: {
      Authorization: `Bearer ${token}`,
      Origin: ORIGIN,
      ...(orgId ? { "X-Prabhix-Org": orgId } : {}),
    },
  });

  if (!res.ok) {
    return { path, status: res.status, ok: false, detail: await res.text() };
  }

  const json: unknown = await res.json();
  const parsed = schema.safeParse(json);
  if (parsed.success) return { path, status: res.status, ok: true, detail: "" };

  return {
    path,
    status: res.status,
    ok: false,
    detail: parsed.error.errors
      .map((e) => `${e.path.join(".") || "root"}: ${e.message}`)
      .join(" | "),
  };
}

describe.skipIf(!enabled)("live API matches client schemas", () => {
  it("every console endpoint parses", async () => {
    const cases: [string, ZodTypeAny][] = [
      ["/dashboard", dashboardSchema],
      ["/users/me", userProfileSchema],
      ["/users/me/sessions", sessionListSchema],
      [`/organizations/${orgId}`, organizationViewSchema],
      [`/organizations/${orgId}/members?limit=20`, memberListPageSchema],
      ["/flags", effectiveFlagsSchema],
      ["/ai/status", aiAvailabilitySchema],
      ["/ai/settings", aiOrgSettingsSchema],
      ["/ai/prompts", aiPromptListSchema],
      ["/ai/usage/summary", aiUsageSummarySchema],
      ["/ai/usage?limit=20", aiUsagePageSchema],
      ["/audit-logs?limit=20", auditLogPageSchema],
      ["/event-logs?limit=20", eventLogPageSchema],
      ["/billing/subscription", subscriptionSchema],
      ["/billing/plans", planPageSchema],
      ["/billing/entitlements", entitlementsSchema],
      ["/billing/invoices?limit=20", invoiceListPageSchema],
      ["/billing/payment-methods", paymentMethodListSchema],
      ["/billing/address", billingAddressSchema],
      ["/chat/conversations?limit=20", conversationListPageSchema],
      ["/chat/conversations/counts", chatInboxCountsSchema],
      ["/chat/canned-replies", chatCannedReplyListSchema],
      ["/chat/settings", chatSettingsSchema],
      ["/commerce/dashboard", dashboardViewSchema],
      ["/commerce/products?limit=20", productListPageSchema],
      ["/commerce/orders?limit=20", orderListPageSchema],
      ["/commerce/customers?limit=20", customerListPageSchema],
      ["/commerce/discounts", discountListSchema],
      ["/commerce/settings", settingsViewSchema],
      ["/mail/threads?limit=20", threadListPageSchema],
      ["/mail/mailboxes", mailboxListSchema],
      ["/mail/domains", domainListSchema],
      ["/mail/templates", templateListSchema],
      ["/mail/tags", mailTagListSchema],
      ["/mail/canned-replies", cannedReplyListSchema],
      ["/visitors?limit=20", visitorListPageSchema],
      ["/visitors/live", liveVisitorListSchema],
      ["/visitors/analytics/summary?days=7", visitorAnalyticsSummarySchema],
      ["/roles", rolePageSchema],
      ["/teams", teamPageSchema],
      ["/invites", invitePageSchema],
      ["/settings/api-keys", apiKeyPageSchema],
      ["/admin/platform/overview", platformOverviewSchema],
      ["/admin/platform/tenants?limit=20", tenantPageSchema],
      ["/admin/site/leads?limit=20", leadPageSchema],
      ["/admin/site/subscribers?limit=20", subscriberPageSchema],
      ["/admin/site/applications?limit=20", applicationPageSchema],
    ];

    const results = [];
    for (const [path, schema] of cases) {
      results.push(await check(path, schema));
    }

    const failures = results.filter((r) => !r.ok);
    // eslint-disable-next-line no-console
    console.log(
      `\nchecked ${results.length} endpoints, ${failures.length} failed\n` +
        failures.map((f) => `  FAIL ${f.path} [${f.status}] ${f.detail.slice(0, 400)}`).join("\n"),
    );

    expect(failures).toEqual([]);
  }, 180_000);
});
