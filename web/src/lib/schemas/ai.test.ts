import { describe, expect, it } from "vitest";
import {
  aiStreamEventSchema,
  aiTriageSuggestionSchema,
  hasCachedTriage,
} from "@/lib/schemas/ai";

describe("ai schemas", () => {
  it("parses ai.delta stream events", () => {
    const event = aiStreamEventSchema.parse({
      type: "ai.delta",
      threadId: "thread-1",
      conversationId: null,
      payload: { delta: "Hello", finished: false },
    });
    expect(event.type).toBe("ai.delta");
    expect(event.payload.delta).toBe("Hello");
  });

  it("detects cached triage suggestions", () => {
    const empty = aiTriageSuggestionSchema.parse({
      available: false,
      suggestedTags: [],
      suggestedPriority: null,
      intent: null,
    });
    expect(hasCachedTriage(empty)).toBe(false);

    const cached = aiTriageSuggestionSchema.parse({
      available: true,
      suggestedTags: ["billing"],
      suggestedPriority: "HIGH",
      intent: "refund request",
      confidence: 0.91,
    });
    expect(hasCachedTriage(cached)).toBe(true);
  });
});
