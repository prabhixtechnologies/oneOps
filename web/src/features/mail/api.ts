import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest, apiRequestVoid, apiUpload } from "@/lib/api-client";
import { dashboardSchema } from "@/lib/schemas/billing";
import {
  cannedReplyListSchema,
  cannedReplySchema,
  dnsRecordsResponseSchema,
  domainListSchema,
  effectiveFlagsSchema,
  fileUploadSchema,
  flagDetailSchema,
  mailDomainSchema,
  mailboxDetailSchema,
  mailboxListSchema,
  mailTagListSchema,
  mailTagSchema,
  messageSummarySchema,
  routingRuleSchema,
  templateDetailSchema,
  templateListSchema,
  templatePreviewSchema,
  threadDetailResponseSchema,
  threadListPageSchema,
  threadSummarySchema,
  templateKey,
} from "@/lib/schemas/mail";
import { z } from "zod";

export function useDashboard() {
  return useQuery({
    queryKey: ["dashboard"],
    queryFn: () => apiRequest("/dashboard", dashboardSchema),
  });
}

export function useMailboxes() {
  return useQuery({
    queryKey: ["mailboxes"],
    queryFn: () => apiRequest("/mail/mailboxes", mailboxListSchema),
  });
}

export function useMailbox(id: string | undefined) {
  return useQuery({
    queryKey: ["mailbox", id],
    queryFn: () => apiRequest(`/mail/mailboxes/${id}`, mailboxDetailSchema),
    enabled: !!id,
  });
}

export function useDeleteMailbox() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiRequestVoid(`/mail/mailboxes/${id}`, { method: "DELETE" }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["mailboxes"] }),
  });
}

export function useAddMailboxMember() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      mailboxId,
      userId,
      accessLevel,
    }: {
      mailboxId: string;
      userId: string;
      accessLevel?: "MEMBER" | "LEAD";
    }) =>
      apiRequest(`/mail/mailboxes/${mailboxId}/members`, z.object({ userId: z.string() }), {
        method: "POST",
        body: { userId, accessLevel: accessLevel ?? "MEMBER" },
      }),
    onSuccess: (_, { mailboxId }) => void qc.invalidateQueries({ queryKey: ["mailbox", mailboxId] }),
  });
}

export function useRemoveMailboxMember() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ mailboxId, userId }: { mailboxId: string; userId: string }) =>
      apiRequestVoid(`/mail/mailboxes/${mailboxId}/members/${userId}`, { method: "DELETE" }),
    onSuccess: (_, { mailboxId }) => void qc.invalidateQueries({ queryKey: ["mailbox", mailboxId] }),
  });
}

const issuedMailPasswordSchema = z.object({
  address: z.string(),
  password: z.string(),
  issuedAt: z.string(),
});

/**
 * Issues the password a mail client (Thunderbird, Apple Mail, a phone) uses for IMAP and SMTP.
 *
 * Deliberately not cached and never refetched: the plaintext exists only in this one response, so
 * putting it in the query cache would keep a live credential in memory for every subsequent render.
 * The caller shows it once and drops it.
 */
export function useIssueMailPassword() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (mailboxId: string) =>
      apiRequest(`/mail/mailboxes/${mailboxId}/mail-password`, issuedMailPasswordSchema, {
        method: "POST",
      }),
    onSuccess: (_, mailboxId) => void qc.invalidateQueries({ queryKey: ["mailbox", mailboxId] }),
  });
}

export function useRevokeMailPassword() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (mailboxId: string) =>
      apiRequestVoid(`/mail/mailboxes/${mailboxId}/mail-password`, { method: "DELETE" }),
    onSuccess: (_, mailboxId) => void qc.invalidateQueries({ queryKey: ["mailbox", mailboxId] }),
  });
}

export function useThreads(filters: Record<string, string | undefined>) {
  const params = new URLSearchParams({ limit: "25" });
  for (const [k, v] of Object.entries(filters)) {
    if (v) params.set(k, v);
  }

  return useInfiniteQuery({
    queryKey: ["threads", filters],
    queryFn: ({ pageParam }) => {
      const qs = new URLSearchParams(params);
      if (pageParam) qs.set("cursor", pageParam);
      return apiRequest(`/mail/threads?${qs}`, threadListPageSchema);
    },
    initialPageParam: null as string | null,
    getNextPageParam: (last) => (last.hasMore ? last.nextCursor : undefined),
  });
}

export function useThread(id: string | undefined) {
  return useQuery({
    queryKey: ["thread", id],
    queryFn: () => apiRequest(`/mail/threads/${id}`, threadDetailResponseSchema),
    enabled: !!id,
  });
}

export function useCannedReplies() {
  return useQuery({
    queryKey: ["canned-replies"],
    queryFn: () => apiRequest("/mail/canned-replies", cannedReplyListSchema),
  });
}

export function useCreateCannedReply() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { title: string; bodyHtml: string; subject?: string; shortcut?: string }) =>
      apiRequest("/mail/canned-replies", cannedReplySchema, { method: "POST", body: data }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["canned-replies"] }),
  });
}

export function useMailTags() {
  return useQuery({
    queryKey: ["mail-tags"],
    queryFn: () => apiRequest("/mail/tags", mailTagListSchema),
  });
}

export function useCreateMailTag() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { name: string; slug?: string; colour?: string; description?: string }) =>
      apiRequest("/mail/tags", mailTagSchema, { method: "POST", body: data }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["mail-tags"] }),
  });
}

export function useFeatureFlags() {
  return useQuery({
    queryKey: ["flags"],
    queryFn: () => apiRequest("/flags", effectiveFlagsSchema),
  });
}

export function useSetFeatureFlag() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ key, enabled }: { key: string; enabled: boolean }) =>
      apiRequest(`/flags/${key}`, flagDetailSchema, { method: "PUT", body: { enabled } }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["flags"] }),
  });
}

export function useUploadFile() {
  return useMutation({
    mutationFn: ({
      file,
      purpose,
    }: {
      file: File;
      purpose?: "MAIL_ATTACHMENT" | "AVATAR" | "LOGO" | "EXPORT" | "IMPORT" | "INVOICE";
    }) => apiUpload("/files", file, fileUploadSchema, { purpose }),
  });
}

export function useDeleteFile() {
  return useMutation({
    mutationFn: (id: string) => apiRequestVoid(`/files/${id}`, { method: "DELETE" }),
  });
}

export function useReplyToThread() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      threadId,
      to,
      bodyHtml,
      cc,
      subject,
      replyMode,
      attachmentIds,
    }: {
      threadId: string;
      to: string[];
      bodyHtml: string;
      cc?: string[];
      subject?: string;
      replyMode?: "REPLY" | "REPLY_ALL" | "FORWARD";
      attachmentIds?: string[];
    }) =>
      apiRequest(`/mail/threads/${threadId}/reply`, messageSummarySchema, {
        method: "POST",
        body: {
          to,
          bodyHtml,
          cc,
          subject,
          replyMode: replyMode ?? "REPLY",
          attachmentIds: attachmentIds ?? [],
        },
      }),
    onSuccess: (_, { threadId }) => {
      void qc.invalidateQueries({ queryKey: ["thread", threadId] });
      void qc.invalidateQueries({ queryKey: ["threads"] });
    },
  });
}

export function useUpdateThread() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      threadId,
      data,
    }: {
      threadId: string;
      data: { status?: string; priority?: string };
    }) =>
      apiRequest(`/mail/threads/${threadId}`, threadSummarySchema, {
        method: "PATCH",
        body: data,
      }),
    onSuccess: (_, { threadId }) => {
      void qc.invalidateQueries({ queryKey: ["thread", threadId] });
      void qc.invalidateQueries({ queryKey: ["threads"] });
    },
  });
}

export function useBulkUpdateThreads() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { threadIds: string[]; status?: string; priority?: string }) =>
      apiRequest("/mail/threads/bulk", z.number(), { method: "POST", body: data }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["threads"] }),
  });
}

export function useAssignThread() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      threadId,
      userId,
      teamId,
    }: {
      threadId: string;
      userId?: string;
      teamId?: string;
    }) =>
      apiRequestVoid(`/mail/threads/${threadId}/assign`, {
        method: "POST",
        body: { userId, teamId },
      }),
    onSuccess: (_, { threadId }) => {
      void qc.invalidateQueries({ queryKey: ["thread", threadId] });
      void qc.invalidateQueries({ queryKey: ["threads"] });
    },
  });
}

export function useDomains() {
  return useQuery({
    queryKey: ["domains"],
    queryFn: () => apiRequest("/mail/domains", domainListSchema),
  });
}

export function useDomainDns(id: string | undefined) {
  return useQuery({
    queryKey: ["domain-dns", id],
    queryFn: () => apiRequest(`/mail/domains/${id}/dns`, dnsRecordsResponseSchema),
    enabled: !!id,
  });
}

export function useVerifyDomain() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      apiRequest(`/mail/domains/${id}/verify`, dnsRecordsResponseSchema, { method: "POST" }),
    onSuccess: (_, id) => {
      void qc.invalidateQueries({ queryKey: ["domain-dns", id] });
      void qc.invalidateQueries({ queryKey: ["domains"] });
    },
  });
}

export function useTemplates() {
  return useQuery({
    queryKey: ["templates"],
    queryFn: () => apiRequest("/mail/templates", templateListSchema),
  });
}

export function useTemplate(key: string | undefined) {
  return useQuery({
    queryKey: ["template", key],
    queryFn: () =>
      apiRequest(`/mail/templates/${encodeURIComponent(key ?? "")}`, templateDetailSchema),
    enabled: !!key,
  });
}

export function useUpdateTemplate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      key,
      data,
    }: {
      key: string;
      data: { name?: string; subject?: string; htmlBody?: string; textBody?: string };
    }) =>
      apiRequest(`/mail/templates/${encodeURIComponent(key)}`, templateDetailSchema, {
        method: "PATCH",
        body: data,
      }),
    onSuccess: (_, { key }) => void qc.invalidateQueries({ queryKey: ["template", key] }),
  });
}

export function usePreviewTemplate() {
  return useMutation({
    mutationFn: ({ key, variables }: { key: string; variables: Record<string, string> }) =>
      apiRequest(
        `/mail/templates/${encodeURIComponent(key)}/preview`,
        templatePreviewSchema,
        { method: "POST", body: { variables } },
      ),
  });
}

export function useUpdateMailbox() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: Record<string, unknown> }) =>
      apiRequest(`/mail/mailboxes/${id}`, mailboxDetailSchema, { method: "PATCH", body: data }),
    onSuccess: (_, { id }) => {
      void qc.invalidateQueries({ queryKey: ["mailbox", id] });
      void qc.invalidateQueries({ queryKey: ["mailboxes"] });
    },
  });
}

export function useUpdateRoutingRules() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, rules }: { id: string; rules: z.infer<typeof routingRuleSchema>[] }) =>
      apiRequest(`/mail/mailboxes/${id}`, mailboxDetailSchema, {
        method: "PATCH",
        body: { routingRules: rules },
      }),
    onSuccess: (_, { id }) => {
      void qc.invalidateQueries({ queryKey: ["mailbox", id] });
    },
  });
}

export function useCreateDomain() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (domain: string) =>
      apiRequest("/mail/domains", mailDomainSchema, { method: "POST", body: { domain } }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["domains"] }),
  });
}

export { templateKey };
