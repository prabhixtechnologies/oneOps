import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest, apiRequestVoid } from "@/lib/api-client";
import {
  chatCannedReplyListSchema,
  chatCannedReplySchema,
  chatInboxCountsSchema,
  chatMessageListPageSchema,
  chatMessageSchema,
  chatSettingsSchema,
  conversationDetailSchema,
  conversationListPageSchema,
  conversationSummarySchema,
} from "@/lib/schemas/chat";

export function useChatConversations(filters: {
  queue?: string;
  status?: string;
  limit?: number;
}) {
  const params = new URLSearchParams({ limit: String(filters.limit ?? 50) });
  if (filters.queue) params.set("queue", filters.queue);
  if (filters.status) params.set("status", filters.status);

  return useInfiniteQuery({
    queryKey: ["chat-conversations", filters],
    queryFn: ({ pageParam }) => {
      const qs = new URLSearchParams(params);
      if (pageParam) qs.set("cursor", pageParam);
      return apiRequest(`/chat/conversations?${qs}`, conversationListPageSchema);
    },
    initialPageParam: null as string | null,
    getNextPageParam: (last) => (last.hasMore ? last.nextCursor ?? undefined : undefined),
  });
}

export function useChatCounts() {
  return useQuery({
    queryKey: ["chat-counts"],
    queryFn: () => apiRequest("/chat/conversations/counts", chatInboxCountsSchema),
    refetchInterval: 30_000,
  });
}

export function useChatConversation(id: string | undefined) {
  return useQuery({
    queryKey: ["chat-conversation", id],
    queryFn: () => apiRequest(`/chat/conversations/${id}`, conversationDetailSchema),
    enabled: !!id,
  });
}

export function useChatMessages(conversationId: string | undefined) {
  return useInfiniteQuery({
    queryKey: ["chat-messages", conversationId],
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams({ limit: "50" });
      if (pageParam) params.set("cursor", pageParam);
      return apiRequest(
        `/chat/conversations/${conversationId}/messages?${params}`,
        chatMessageListPageSchema,
      );
    },
    initialPageParam: null as string | null,
    getNextPageParam: (last) => (last.hasMore ? last.nextCursor ?? undefined : undefined),
    enabled: !!conversationId,
  });
}

export function useSendChatMessage() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      conversationId,
      body,
      internal,
      fileId,
    }: {
      conversationId: string;
      body: string;
      internal?: boolean;
      fileId?: string;
    }) => {
      const params = internal ? "?note=true" : "";
      return apiRequest(`/chat/conversations/${conversationId}/messages${params}`, chatMessageSchema, {
        method: "POST",
        body: { body, internal: internal ?? false, fileId },
      });
    },
    onSuccess: (_, { conversationId }) => {
      void qc.invalidateQueries({ queryKey: ["chat-conversation", conversationId] });
      void qc.invalidateQueries({ queryKey: ["chat-messages", conversationId] });
      void qc.invalidateQueries({ queryKey: ["chat-conversations"] });
      void qc.invalidateQueries({ queryKey: ["chat-counts"] });
    },
  });
}

export function useAssignChatConversation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ conversationId, agentId }: { conversationId: string; agentId: string }) =>
      apiRequest(`/chat/conversations/${conversationId}/assign`, conversationSummarySchema, {
        method: "POST",
        body: { agentId },
      }),
    onSuccess: (_, { conversationId }) => {
      void qc.invalidateQueries({ queryKey: ["chat-conversation", conversationId] });
      void qc.invalidateQueries({ queryKey: ["chat-conversations"] });
      void qc.invalidateQueries({ queryKey: ["chat-counts"] });
    },
  });
}

export function useUpdateChatConversation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      conversationId,
      data,
    }: {
      conversationId: string;
      data: { status?: string; priority?: string; tags?: string[] };
    }) =>
      apiRequest(`/chat/conversations/${conversationId}`, conversationSummarySchema, {
        method: "PATCH",
        body: data,
      }),
    onSuccess: (_, { conversationId }) => {
      void qc.invalidateQueries({ queryKey: ["chat-conversation", conversationId] });
      void qc.invalidateQueries({ queryKey: ["chat-conversations"] });
    },
  });
}

export function useChatSettings() {
  return useQuery({
    queryKey: ["chat-settings"],
    queryFn: () => apiRequest("/chat/settings", chatSettingsSchema),
  });
}

export function useUpdateChatSettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: {
      availability?: string;
      awayMessage?: string;
      businessHours?: Record<string, unknown>;
      preChatEnabled?: boolean;
      offlineMailboxId?: string | null;
    }) => apiRequest("/chat/settings", chatSettingsSchema, { method: "PATCH", body: data }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["chat-settings"] }),
  });
}

export function useChatCannedReplies() {
  return useQuery({
    queryKey: ["chat-canned-replies"],
    queryFn: () => apiRequest("/chat/canned-replies", chatCannedReplyListSchema),
  });
}

export function useCreateChatCannedReply() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { title: string; body: string; shortcut?: string }) =>
      apiRequest("/chat/canned-replies", chatCannedReplySchema, { method: "POST", body: data }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["chat-canned-replies"] }),
  });
}

export function useDeleteChatCannedReply() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiRequestVoid(`/chat/canned-replies/${id}`, { method: "DELETE" }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["chat-canned-replies"] }),
  });
}
