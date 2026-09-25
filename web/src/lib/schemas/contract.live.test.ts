/**
 * Contract check: every console endpoint, fetched for real and parsed with the schema the app uses.
 *
 * Live hits skip unless CONTRACT_LIVE=1 or PBX_CONTRACT_EMAIL and PBX_CONTRACT_PASSWORD are set.
 * Fixture checks in this file always run.
 *
 *   CONTRACT_LIVE=1 PBX_CONTRACT_EMAIL=you@example.com PBX_CONTRACT_PASSWORD=... \
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
  cartViewSchema,
  customerListPageSchema,
  dashboardViewSchema,
  discountListSchema,
  orderListPageSchema,
  productListPageSchema,
  settingsViewSchema,
} from "./commerce";
import { organizationViewSchema, userProfileSchema } from "./common";
import { eventLogPageSchema } from "./logs";
import { effectiveFlagsSchema } from "./flags";
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
const liveRequested = process.env.CONTRACT_LIVE === "1";
const enabled = liveRequested || (EMAIL !== "" && PASSWORD !== "");

let token = "";
let orgId = "";

describe("mocked commerce schemas", () => {
  it("parses a storefront cart the BFF would return (token stripped)", () => {
    const parsed = cartViewSchema.parse({
      currency: "INR",
      items: [],
      subtotalPaise: 0,
      discountPaise: 0,
      taxPaise: 0,
      shippingPaise: 0,
      totalPaise: 0,
      expiresAt: "2026-12-31T00:00:00Z",
    });
    expect(parsed.totalPaise).toBe(0);
    expect(parsed.currency).toBe("INR");
  });

  it("parses a product summary page", () => {
    const parsed = productListPageSchema.parse({
      items: [
        {
          id: "17e45928-4888-47e6-9f72-554b0042c408",
          slug: "oneops",
          name: "OneOps",
          productType: "SUBSCRIPTION",
          featured: true,
          fromPricePaise: 9900,
          currency: "INR",
        },
      ],
      hasMore: false,
    });
    expect(parsed.items).toHaveLength(1);
    expect(parsed.nextCursor).toBeNull();
  });
});

beforeAll(async () => {
  if (!enabled) return;
  if (liveRequested && (EMAIL === "" || PASSWORD === "")) {
    throw new Error("CONTRACT_LIVE=1 but PBX_CONTRACT_EMAIL / PBX_CONTRACT_PASSWORD are unset");
  }

  const res = await fetch(`${API}/identity/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Origin: ORIGIN },
    body: JSON.stringify({ email: EMAIL, password: PASSWORD }),
  });

  // Fail here rather than carrying on with no token. Without this, a wrong password produced a
  // report saying all 47 endpoints had schema failures — every one of them a 401 — which reads as
  // the API having broken rather than as the sign-in that actually failed.
  if (!res.ok) {
    throw new Error(
      `Could not sign in to ${API} as ${EMAIL}: ${res.status}. ` +
        `Check PBX_CONTRACT_EMAIL and PBX_CONTRACT_PASSWORD; nothing below ran.`,
    );
  }

  const body = (await res.json()) as { accessToken?: string };
  if (!body.accessToken) {
    throw new Error("Sign-in succeeded but returned no accessToken, so no endpoint could be checked");
  }
  token = body.accessToken;

  const me = await fetch(`${API}/oneops/auth/me`, {
    headers: { Authorization: `Bearer ${token}`, Origin: ORIGIN },
  });
  const meBody = (await me.json()) as { organizationId?: string; memberships?: { orgId: string }[] };
  orgId = meBody.organizationId ?? meBody.memberships?.[0]?.orgId ?? "";

  // Two of the cases interpolate this, and an empty one silently requests `/organizations/`.
  if (!orgId) {
    throw new Error("Signed in, but /auth/me named no organization, so the org-scoped cases cannot run");
  }
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
    return { path, status: res.status, ok: false, empty: false, detail: await res.text() };
  }

  const json: unknown = await res.json();

  // An endpoint that returns no rows validates trivially, which is worth knowing: passing here says
  // nothing about the item schema. Every mismatch this file was written to find was inside a list
  // item, so a green run over empty collections is not evidence that those pages work.
  const empty = Array.isArray(json)
    ? json.length === 0
    : Array.isArray((json as { items?: unknown[] })?.items)
      ? (json as { items: unknown[] }).items.length === 0
      : false;

  const parsed = schema.safeParse(json);
  if (parsed.success) return { path, status: res.status, ok: true, empty, detail: "" };

  return {
    path,
    status: res.status,
    ok: false,
    empty,
    detail: parsed.error.errors
      .map((e) => `${e.path.join(".") || "root"}: ${e.message}`)
      .join(" | "),
  };
}

describe.skipIf(!enabled)("live API matches client schemas", () => {
  it("every console endpoint parses", async () => {
    const cases: [string, ZodTypeAny][] = [
      ["/oneops/dashboard", dashboardSchema],
      ["/oneops/users/me", userProfileSchema],
      ["/oneops/users/me/sessions", sessionListSchema],
      [`/oneops/organizations?id=${orgId}`, organizationViewSchema],
      [`/oneops/organizations/members?orgId=${orgId}&limit=20`, memberListPageSchema],
      ["/oneops/flags", effectiveFlagsSchema],
      ["/oneops/ai/status", aiAvailabilitySchema],
      ["/oneops/ai/settings", aiOrgSettingsSchema],
      ["/oneops/ai/prompts", aiPromptListSchema],
      ["/oneops/ai/usage/summary", aiUsageSummarySchema],
      ["/oneops/ai/usage?limit=20", aiUsagePageSchema],
      ["/oneops/audit-logs?limit=20", auditLogPageSchema],
      ["/oneops/event-logs?limit=20", eventLogPageSchema],
      ["/oneops/billing/subscription", subscriptionSchema],
      ["/oneops/billing/plans", planPageSchema],
      ["/oneops/billing/entitlements", entitlementsSchema],
      ["/oneops/billing/invoices?limit=20", invoiceListPageSchema],
      ["/oneops/billing/payment-methods", paymentMethodListSchema],
      ["/oneops/billing/address", billingAddressSchema],
      ["/oneops/chat/conversations?limit=20", conversationListPageSchema],
      ["/oneops/chat/conversations/counts", chatInboxCountsSchema],
      ["/oneops/chat/canned-replies", chatCannedReplyListSchema],
      ["/oneops/chat/settings", chatSettingsSchema],
      ["/oneops/commerce/dashboard", dashboardViewSchema],
      ["/oneops/commerce/products?limit=20", productListPageSchema],
      ["/oneops/commerce/orders?limit=20", orderListPageSchema],
      ["/oneops/commerce/customers?limit=20", customerListPageSchema],
      ["/oneops/commerce/discounts", discountListSchema],
      ["/oneops/commerce/settings", settingsViewSchema],
      ["/oneops/visitors?limit=20", visitorListPageSchema],
      ["/oneops/visitors/live", liveVisitorListSchema],
      ["/oneops/visitors/analytics/summary?days=7", visitorAnalyticsSummarySchema],
      ["/oneops/roles", rolePageSchema],
      ["/oneops/teams", teamPageSchema],
      ["/oneops/invites", invitePageSchema],
      ["/oneops/settings/api-keys", apiKeyPageSchema],
      ["/oneops/admin/platform/overview", platformOverviewSchema],
      ["/oneops/admin/platform/tenants?limit=20", tenantPageSchema],
      ["/oneops/admin/site/leads?limit=20", leadPageSchema],
      ["/oneops/admin/site/subscribers?limit=20", subscriberPageSchema],
      ["/oneops/admin/site/applications?limit=20", applicationPageSchema],
    ];

    const results = [];
    for (const [path, schema] of cases) {
      results.push(await check(path, schema));
    }

    const failures = results.filter((r) => !r.ok);
    const unexercised = results.filter((r) => r.ok && r.empty);

    // Written straight to stdout because vitest intercepts console.log, and the coverage caveat
    // below is the whole point of running this — a pass over empty collections proves very little.
    const report =
      `\ncontract check: ${results.length} endpoints, ${failures.length} failed\n` +
      failures.map((f) => `  FAIL  ${f.path} [${f.status}] ${f.detail.slice(0, 400)}`).join("\n") +
      `\n${unexercised.length} returned no rows, so their item schemas are NOT verified:\n` +
      unexercised.map((r) => `  EMPTY ${r.path}`).join("\n") +
      "\n";
    process.stdout.write(report);

    expect(failures, report).toEqual([]);
  }, 180_000);
});
