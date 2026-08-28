import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api-client";
import {
  aiAssistResultSchema,
  aiAvailabilitySchema,
  aiDraftSuggestionSchema,
  aiHandoffSummaryResultSchema,
  aiOrgSettingsSchema,
  aiPromptListSchema,
  aiPromptSchema,
  aiRewriteResultSchema,
  aiSentimentResultSchema,
  aiTextResultSchema,
  aiTriageSuggestionSchema,
  aiUsagePageSchema,
  aiUsageSummarySchema,
} from "@/lib/schemas/ai";

export const aiQueryKeys = {
  status: ["ai", "status"] as const,
  settings: ["ai", "settings"] as const,
  prompts: ["ai", "prompts"] as const,
  usageSummary: ["ai", "usage", "summary"] as const,
  usage: (cursor?: string | null) => ["ai", "usage", cursor] as const,
  mailTriage: (threadId: string) => ["ai", "mail", "triage", threadId] as const,
};

export function useAiStatus() {
  return useQuery({
    queryKey: aiQueryKeys.status,
    queryFn: () => apiRequest("/ai/status", aiAvailabilitySchema),
    staleTime: 60_000,
  });
}

export function useAiSettings() {
  return useQuery({
    queryKey: aiQueryKeys.settings,
    queryFn: () => apiRequest("/ai/settings", aiOrgSettingsSchema),
  });
}

export function useUpdateAiSettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: {
      preferredProvider?: string | null;
      preferredChatModel?: string | null;
      preferredReasoningModel?: string | null;
      firstResponderEnabled?: boolean;
    }) => apiRequest("/ai/settings", aiOrgSettingsSchema, { method: "PATCH", body: data }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: aiQueryKeys.settings });
      void qc.invalidateQueries({ queryKey: aiQueryKeys.status });
    },
  });
}

export function useAiPrompts() {
  return useQuery({
    queryKey: aiQueryKeys.prompts,
    queryFn: () => apiRequest("/ai/prompts", aiPromptListSchema),
  });
}

export function useUpdateAiPrompt() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      taskKey,
      template,
      provider,
      model,
      temperature,
    }: {
      taskKey: string;
      template: string;
      provider?: string;
      model?: string;
      temperature?: number;
    }) =>
      apiRequest(`/ai/prompts/${encodeURIComponent(taskKey)}`, aiPromptSchema, {
        method: "PUT",
        body: { template, provider, model, temperature },
      }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: aiQueryKeys.prompts }),
  });
}

export function useAiUsageSummary() {
  return useQuery({
    queryKey: aiQueryKeys.usageSummary,
    queryFn: () => apiRequest("/ai/usage/summary", aiUsageSummarySchema),
  });
}

export function useAiUsage() {
  return useInfiniteQuery({
    queryKey: ["ai", "usage", "list"],
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams({ limit: "25" });
      if (pageParam) params.set("cursor", pageParam);
      return apiRequest(`/ai/usage?${params}`, aiUsagePageSchema);
    },
    initialPageParam: null as string | null,
    getNextPageParam: (last) => (last.hasMore ? last.nextCursor : undefined),
  });
}

export function useMailThreadTriage(threadId: string | undefined) {
  return useQuery({
    queryKey: aiQueryKeys.mailTriage(threadId ?? ""),
    queryFn: () => apiRequest(`/mail/threads/${threadId}/ai/triage`, aiTriageSuggestionSchema),
    enabled: !!threadId,
    staleTime: 30_000,
  });
}

export function useMailSummarizeThread() {
  return useMutation({
    mutationFn: (threadId: string) =>
      apiRequest(`/mail/threads/${threadId}/ai/summarize`, aiTextResultSchema, { method: "POST" }),
  });
}

export function useMailTriageThread() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (threadId: string) =>
      apiRequest(`/mail/threads/${threadId}/ai/triage`, aiTriageSuggestionSchema, { method: "POST" }),
    onSuccess: (_, threadId) => void qc.invalidateQueries({ queryKey: aiQueryKeys.mailTriage(threadId) }),
  });
}

export function useMailAdaptCannedReply() {
  return useMutation({
    mutationFn: ({ threadId, cannedReplyId }: { threadId: string; cannedReplyId: string }) =>
      apiRequest(
        `/mail/threads/${threadId}/ai/canned-replies/${cannedReplyId}/adapt`,
        aiDraftSuggestionSchema,
        { method: "POST" },
      ),
  });
}

export function useChatRewrite() {
  return useMutation({
    mutationFn: ({
      conversationId,
      draft,
      action,
    }: {
      conversationId: string;
      draft: string;
      action: string;
    }) =>
      apiRequest(`/chat/conversations/${conversationId}/ai/rewrite`, aiRewriteResultSchema, {
        method: "POST",
        body: { draft, action },
      }),
  });
}

export function useChatHandoffSummary() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (conversationId: string) =>
      apiRequest(`/chat/conversations/${conversationId}/ai/handoff-summary`, aiHandoffSummaryResultSchema, {
        method: "POST",
      }),
    onSuccess: (_, conversationId) => {
      void qc.invalidateQueries({ queryKey: ["chat-conversation", conversationId] });
    },
  });
}

export function useChatSentiment(conversationId: string | undefined) {
  return useQuery({
    queryKey: ["ai", "chat", "sentiment", conversationId],
    queryFn: () =>
      apiRequest(`/chat/conversations/${conversationId}/ai/sentiment`, aiSentimentResultSchema, {
        method: "POST",
      }),
    enabled: !!conversationId,
    staleTime: 120_000,
  });
}

export function useAiAssist() {
  return useMutation({
    mutationFn: (body: {
      instruction: string;
      context?: string;
      taskKey?: string;
      providerOverride?: string;
    }) => apiRequest("/ai/assist", aiAssistResultSchema, { method: "POST", body }),
  });
}
