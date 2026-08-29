import { describe, expect, it } from "vitest";
import { dashboardSchema, subscriptionSchema } from "./billing";
import { organizationViewSchema } from "./common";
import { apiKeySchema } from "./org";
import { aiDraftSuggestionSchema, aiOrgSettingsSchema } from "./ai";

/**
 * The API omits null fields instead of sending them (Jackson `non_null`), so every response schema
 * has to accept an absent key wherever the value can be unset. Each case below is a payload the
 * server really produces; before these schemas moved to `.nullish()` they threw SCHEMA_MISMATCH and
 * the page showed a bare "failed to load" with no clue which field was at fault.
 */
describe("response schemas tolerate omitted null fields", () => {
  it("accepts dashboard activity that has no actor", () => {
    const payload = {
      kpis: {
        openThreads: 0,
        avgFirstResponseMinutes: 0,
        slaBreaches: 0,
        seatsUsed: 1,
        seatsLimit: 3,
        mrr: 0,
        currency: "INR",
      },
      recentActivity: [
        {
          id: "17e45928-4888-47e6-9f72-554b0042c408",
          type: "chat.conversation.started",
          description: "chat conversation started",
          createdAt: "2026-08-28T13:44:13.532677Z",
        },
      ],
      threadsTrend: [{ date: "2026-08-16", value: 0 }],
      responseTimeTrend: [{ date: "2026-08-16", value: 0 }],
    };

    const parsed = dashboardSchema.parse(payload);

    expect(parsed.recentActivity[0]?.actor).toBeUndefined();
  });

  it("still accepts an explicit null actor", () => {
    const activity = {
      id: "a",
      type: "t",
      description: "d",
      actor: null,
      createdAt: "2026-08-28T13:44:13Z",
    };

    expect(() =>
      dashboardSchema.parse({
        kpis: {
          openThreads: 0,
          avgFirstResponseMinutes: 0,
          slaBreaches: 0,
          seatsUsed: 1,
          seatsLimit: 1,
          mrr: 0,
          currency: "INR",
        },
        recentActivity: [activity],
        threadsTrend: [],
        responseTimeTrend: [],
      }),
    ).not.toThrow();
  });

  it("accepts an organization that is not on a trial", () => {
    expect(() =>
      organizationViewSchema.parse({
        id: "org",
        name: "Prabhix Technologies",
        slug: "prabhix-technologies",
        status: "ACTIVE",
        memberCount: 1,
        seatLimit: 3,
        createdAt: "2026-08-28T13:44:13Z",
      }),
    ).not.toThrow();
  });

  it("accepts a subscription with no trial end", () => {
    expect(() =>
      subscriptionSchema.parse({
        planId: "p",
        planName: "Starter",
        status: "ACTIVE",
        seats: 1,
        currentPeriodEnd: "2026-09-28T13:44:13Z",
      }),
    ).not.toThrow();
  });

  it("accepts an API key that has never been used and never expires", () => {
    expect(() =>
      apiKeySchema.parse({
        id: "k",
        name: "CI",
        prefix: "pbx_live_",
        createdAt: "2026-08-28T13:44:13Z",
      }),
    ).not.toThrow();
  });

  it("accepts AI responses when no provider is configured", () => {
    expect(() =>
      aiDraftSuggestionSchema.parse({ available: false, draft: "" }),
    ).not.toThrow();

    expect(() =>
      aiOrgSettingsSchema.parse({ firstResponderEnabled: false }),
    ).not.toThrow();
  });
});
