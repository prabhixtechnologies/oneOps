import { z } from "zod";
import { arraySchema, cursorPageSchema } from "./common";

export const aiAvailabilitySchema = z.object({
  enabled: z.boolean(),
  configured: z.boolean(),
  tokensUsedThisMonth: z.number(),
  monthlyQuota: z.number(),
});

export const aiDraftSuggestionSchema = z.object({
  available: z.boolean(),
  draft: z.string(),
  provider: z.string().nullish(),
  model: z.string().nullish(),
  piiRedacted: z.boolean().optional(),
  unavailableBecauseNotConfigured: z.boolean().optional(),
});

export const aiTextResultSchema = z.object({
  available: z.boolean(),
  text: z.string(),
  provider: z.string().nullish(),
  model: z.string().nullish(),
  unavailableBecauseNotConfigured: z.boolean().optional(),
});

export const aiTriageSuggestionSchema = z.object({
  available: z.boolean(),
  suggestedTags: z.array(z.string()),
  suggestedPriority: z.string().nullish(),
  intent: z.string().nullish(),
  confidence: z.number().nullable().optional(),
  provider: z.string().nullable().optional(),
  model: z.string().nullable().optional(),
  unavailableBecauseNotConfigured: z.boolean().optional(),
});

export const aiRewriteRequestSchema = z.object({
  draft: z.string(),
  action: z.string(),
});

export const aiRewriteResultSchema = z.object({
  available: z.boolean(),
  text: z.string(),
  provider: z.string().nullish(),
  model: z.string().nullish(),
});

export const aiSentimentResultSchema = z.object({
  available: z.boolean(),
  sentiment: z.string().nullish(),
  urgency: z.string().nullish(),
  summary: z.string().nullish(),
});

export const aiHandoffSummaryResultSchema = z.object({
  available: z.boolean(),
  noteMessageId: z.string().nullish(),
  summary: z.string(),
});

export const aiAssistRequestSchema = z.object({
  instruction: z.string(),
  context: z.string().optional(),
  taskKey: z.string().optional(),
  providerOverride: z.string().optional(),
});

export const aiAssistResultSchema = z.object({
  available: z.boolean(),
  text: z.string(),
  provider: z.string().nullish(),
  model: z.string().nullish(),
});

export const aiUsageSummarySchema = z.object({
  tokensThisMonth: z.number(),
  costPaiseThisMonth: z.number(),
});

export const aiUsageRowSchema = z.object({
  id: z.string(),
  feature: z.string(),
  taskKey: z.string(),
  provider: z.string(),
  model: z.string(),
  totalTokens: z.number(),
  latencyMs: z.number(),
  outcome: z.string(),
  costEstimatePaise: z.number(),
  piiRedacted: z.boolean(),
  createdAt: z.string(),
});

export const aiPromptSchema = z.object({
  id: z.string(),
  taskKey: z.string(),
  name: z.string(),
  description: z.string().nullable().optional(),
  template: z.string(),
  provider: z.string().nullable().optional(),
  model: z.string().nullable().optional(),
  temperature: z.number(),
  version: z.number(),
  orgOverride: z.boolean(),
});

export const aiUpdatePromptRequestSchema = z.object({
  template: z.string(),
  provider: z.string().optional(),
  model: z.string().optional(),
  temperature: z.number().optional(),
});

export const aiOrgSettingsSchema = z.object({
  preferredProvider: z.string().nullish(),
  preferredChatModel: z.string().nullish(),
  preferredReasoningModel: z.string().nullish(),
  firstResponderEnabled: z.boolean(),
});

export const aiUpdateOrgSettingsRequestSchema = z.object({
  preferredProvider: z.string().nullable().optional(),
  preferredChatModel: z.string().nullable().optional(),
  preferredReasoningModel: z.string().nullable().optional(),
  firstResponderEnabled: z.boolean().optional(),
});

export const aiStreamPayloadSchema = z.object({
  delta: z.string().optional(),
  finished: z.boolean().optional(),
  message: z.string().optional(),
});

export const aiStreamEventSchema = z.object({
  type: z.string(),
  conversationId: z.string().nullish(),
  threadId: z.string().nullish(),
  payload: aiStreamPayloadSchema,
});

export type AiAvailability = z.infer<typeof aiAvailabilitySchema>;
export type AiDraftSuggestion = z.infer<typeof aiDraftSuggestionSchema>;
export type AiTextResult = z.infer<typeof aiTextResultSchema>;
export type AiTriageSuggestion = z.infer<typeof aiTriageSuggestionSchema>;
export type AiRewriteResult = z.infer<typeof aiRewriteResultSchema>;
export type AiSentimentResult = z.infer<typeof aiSentimentResultSchema>;
export type AiHandoffSummaryResult = z.infer<typeof aiHandoffSummaryResultSchema>;
export type AiUsageSummary = z.infer<typeof aiUsageSummarySchema>;
export type AiUsageRow = z.infer<typeof aiUsageRowSchema>;
export type AiPrompt = z.infer<typeof aiPromptSchema>;
export type AiOrgSettings = z.infer<typeof aiOrgSettingsSchema>;
export type AiStreamEvent = z.infer<typeof aiStreamEventSchema>;

export const aiPromptListSchema = arraySchema(aiPromptSchema);
export const aiUsagePageSchema = cursorPageSchema(aiUsageRowSchema);

export function isAiUnavailable(result: { available: boolean; unavailableBecauseNotConfigured?: boolean }): boolean {
  return !result.available;
}

export function aiNeedsConfiguration(result: {
  available: boolean;
  unavailableBecauseNotConfigured?: boolean;
}): boolean {
  return !result.available && result.unavailableBecauseNotConfigured === true;
}

export function hasCachedTriage(triage: AiTriageSuggestion): boolean {
  return triage.available && (triage.suggestedTags.length > 0 || !!triage.intent || !!triage.suggestedPriority);
}
