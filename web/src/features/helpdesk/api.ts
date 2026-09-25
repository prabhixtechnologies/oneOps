import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { z } from "zod";
import { apiRequest, apiRequestVoid } from "@/lib/api-client";

/**
 * The shared-mailbox helpdesk API.
 *
 * Separate from mailbox.ts, which is the personal mail client: the two read the same threads through
 * different lenses and it is worth keeping that visible. /mailbox answers "what is in my inbox" and
 * treats a thread as a conversation; /mail/threads answers "what work is outstanding in this shared
 * mailbox" and treats the same thread as a ticket with an assignee, a status and an SLA.
 *
 * There is no separate ticket entity on the backend and there should not be one here either. A ticket
 * is a thread seen from the queue.
 *
 * Schemas rather than interfaces for the same reason as mailbox.ts: parsing at the boundary turns a
 * contract change into one clear error instead of an undefined property three screens later.
 */

export const threadStatuses = [
  "OPEN",
  "PENDING_CUSTOMER",
  "ON_HOLD",
  "RESOLVED",
  "CLOSED",
  "SPAM",
  "TRASH",
] as const;

export type ThreadStatus = (typeof threadStatuses)[number];

export const priorities = ["LOW", "NORMAL", "HIGH", "URGENT"] as const;

export type Priority = (typeof priorities)[number];

/** Statuses an agent moves a ticket between. SPAM and TRASH are dispositions, not workflow. */
export const workflowStatuses: ThreadStatus[] = [
  "OPEN",
  "PENDING_CUSTOMER",
  "ON_HOLD",
  "RESOLVED",
  "CLOSED",
];

export const tagRefSchema = z.object({
  id: z.string(),
  slug: z.string(),
  name: z.string(),
  colour: z.string(),
});

export type TagRef = z.infer<typeof tagRefSchema>;

export const ticketSchema = z.object({
  id: z.string(),
  mailboxId: z.string(),
  referenceKey: z.string(),
  subject: z.string(),
  status: z.enum(threadStatuses),
  priority: z.enum(priorities),
  assigneeUserId: z.string().nullish(),
  assigneeTeamId: z.string().nullish(),
  customerEmail: z.string().nullish(),
  snippet: z.string().nullish(),
  messageCount: z.number(),
  unreadCount: z.number(),
  hasAttachments: z.boolean(),
  lastMessageAt: z.string(),
  lastMessageDirection: z.enum(["INBOUND", "OUTBOUND"]),
  slaDueAt: z.string().nullish(),
  slaBreachedAt: z.string().nullish(),
  firstResponseAt: z.string().nullish(),
  resolvedAt: z.string().nullish(),
  // Defaulted rather than required because web and backend deploy separately. These three fields were
  // added to ThreadSummary at the same time as this screen, so a web build that reaches production
  // first would otherwise fail to parse every ticket against the older backend.
  tags: z.array(tagRefSchema).default([]),
});

export type Ticket = z.infer<typeof ticketSchema>;

export const ticketMessageSchema = z.object({
  id: z.string(),
  direction: z.enum(["INBOUND", "OUTBOUND"]),
  fromAddress: z.string().nullish(),
  fromName: z.string().nullish(),
  subject: z.string().nullish(),
  snippet: z.string().nullish(),
  bodyText: z.string().nullish(),
  bodyHtml: z.string().nullish(),
  deliveryStatus: z.string().nullish(),
  occurredAt: z.string(),
  attachmentCount: z.number(),
});

export type TicketMessage = z.infer<typeof ticketMessageSchema>;

export const noteSchema = z.object({
  id: z.string(),
  authorUserId: z.string().nullish(),
  bodyHtml: z.string(),
  createdAt: z.string(),
});

export type Note = z.infer<typeof noteSchema>;

export const eventTypes = [
  "CREATED",
  "MESSAGE_RECEIVED",
  "MESSAGE_SENT",
  "ASSIGNED",
  "UNASSIGNED",
  "STATUS_CHANGED",
  "PRIORITY_CHANGED",
  "TAG_ADDED",
  "TAG_REMOVED",
  "NOTE_ADDED",
  "SLA_BREACHED",
  "MERGED",
  "MOVED",
  "AUTO_REPLIED",
  "RULE_APPLIED",
] as const;

export type EventType = (typeof eventTypes)[number];

export const eventSchema = z.object({
  // Tolerant of unknown types on purpose. The enum is server-side and additive, and a new event kind
  // should not blank the timeline it appears in.
  eventType: z.enum(eventTypes).catch("RULE_APPLIED"),
  actorUserId: z.string().nullish(),
  actorLabel: z.string().nullish(),
  fromValue: z.string().nullish(),
  toValue: z.string().nullish(),
  createdAt: z.string(),
});

export type TicketEvent = z.infer<typeof eventSchema>;

const ticketDetailSchema = z.object({
  thread: ticketSchema,
  messages: z.array(ticketMessageSchema),
  notes: z.array(noteSchema),
  events: z.array(eventSchema),
});

export type TicketDetail = z.infer<typeof ticketDetailSchema>;

const ticketPageSchema = z.object({
  items: z.array(ticketSchema),
  nextCursor: z.string().nullish(),
  hasMore: z.boolean(),
});

// No description field: TagResponse does not carry one, even though the create request accepts it.
export const tagSchema = z.object({
  id: z.string(),
  slug: z.string(),
  name: z.string(),
  colour: z.string(),
  usageCount: z.number(),
});

export type Tag = z.infer<typeof tagSchema>;

export const cannedReplySchema = z.object({
  id: z.string(),
  mailboxId: z.string().nullish(),
  shortcut: z.string().nullish(),
  title: z.string(),
  subject: z.string().nullish(),
  bodyHtml: z.string(),
  usageCount: z.number(),
});

export type CannedReply = z.infer<typeof cannedReplySchema>;

export const helpdeskMailboxSchema = z.object({
  id: z.string(),
  address: z.string(),
  name: z.string(),
  kind: z.enum(["SHARED", "PERSONAL", "SYSTEM"]),
  status: z.string(),
  openThreadCount: z.number(),
  unassignedCount: z.number(),
});

export type HelpdeskMailbox = z.infer<typeof helpdeskMailboxSchema>;

// -------------------------------------------------------------------------------------------------
// Queries
// -------------------------------------------------------------------------------------------------

export interface TicketFilters {
  mailboxId?: string;
  status?: ThreadStatus;
  priority?: Priority;
  assigneeUserId?: string;
  tagId?: string;
  unreadOnly?: boolean;
  q?: string;
}

export const helpdeskKeys = {
  all: ["helpdesk"] as const,
  mailboxes: ["helpdesk", "mailboxes"] as const,
  tickets: (filters: TicketFilters) => ["helpdesk", "tickets", filters] as const,
  ticket: (id: string) => ["helpdesk", "ticket", id] as const,
  tags: ["helpdesk", "tags"] as const,
  cannedReplies: ["helpdesk", "canned-replies"] as const,
};

function ticketQuery(filters: TicketFilters, cursor?: string): string {
  const params = new URLSearchParams();
  if (filters.mailboxId) params.set("mailboxId", filters.mailboxId);
  if (filters.status) params.set("status", filters.status);
  if (filters.priority) params.set("priority", filters.priority);
  if (filters.assigneeUserId) params.set("assigneeUserId", filters.assigneeUserId);
  if (filters.tagId) params.set("tagId", filters.tagId);
  if (filters.unreadOnly) params.set("unreadOnly", "true");
  if (filters.q) params.set("q", filters.q);
  if (cursor) params.set("cursor", cursor);
  params.set("limit", "40");
  return params.toString();
}

/**
 * The queue, paginated by cursor.
 *
 * Infinite rather than paged because the backend paginates by keyset and a keyset cursor cannot jump
 * to page five — it only knows "after this row". Offering numbered pages over it would mean walking
 * every page to reach one, so the honest UI is "load more".
 */
export function useTickets(filters: TicketFilters) {
  return useInfiniteQuery({
    queryKey: helpdeskKeys.tickets(filters),
    queryFn: ({ pageParam }) =>
      apiRequest(`/oneops/mail/threads?${ticketQuery(filters, pageParam)}`, ticketPageSchema),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    refetchInterval: 60_000,
  });
}

export function useTicket(threadId: string | undefined) {
  return useQuery({
    queryKey: threadId ? helpdeskKeys.ticket(threadId) : ["helpdesk", "ticket", "none"],
    queryFn: () => apiRequest(`/oneops/mail/threads?id=${threadId}`, ticketDetailSchema),
    enabled: !!threadId,
  });
}

export function useHelpdeskMailboxes() {
  return useQuery({
    queryKey: helpdeskKeys.mailboxes,
    queryFn: () => apiRequest("/oneops/mail/mailboxes", z.array(helpdeskMailboxSchema)),
    refetchInterval: 60_000,
  });
}

export function useTags() {
  return useQuery({
    queryKey: helpdeskKeys.tags,
    queryFn: () => apiRequest("/oneops/mail/tags", z.array(tagSchema)),
    staleTime: 5 * 60_000,
  });
}

export function useCannedReplies() {
  return useQuery({
    queryKey: helpdeskKeys.cannedReplies,
    queryFn: () => apiRequest("/oneops/mail/canned-replies", z.array(cannedReplySchema)),
    staleTime: 5 * 60_000,
  });
}

export const memberSchema = z.object({
  userId: z.string(),
  displayName: z.string().nullish(),
  email: z.string().nullish(),
});

export type Member = z.infer<typeof memberSchema>;

const memberPageSchema = z.object({ items: z.array(memberSchema) });

/**
 * Who a ticket can be assigned to.
 *
 * Not retried, and failure is expected rather than exceptional: the members endpoint requires
 * ORG_MEMBER_READ, which an agent who works a queue need not have. Callers treat an error as "no
 * picker" and fall back to assigning to yourself, which is the common case anyway. Retrying a 403
 * three times would only slow the screen down on its way to the same answer.
 */
export function useAssignableMembers(orgId: string | undefined) {
  return useQuery({
    queryKey: ["helpdesk", "members", orgId],
    queryFn: () =>
      apiRequest(`/oneops/organizations/members?orgId=${orgId}&limit=200`, memberPageSchema),
    enabled: !!orgId,
    retry: false,
    staleTime: 5 * 60_000,
  });
}

// -------------------------------------------------------------------------------------------------
// Mutations
// -------------------------------------------------------------------------------------------------

/**
 * Invalidates the ticket and every queue listing.
 *
 * Broad on purpose. Any of these mutations can move a ticket out of the list the agent is looking at
 * — resolving it while filtered to OPEN, reassigning it while filtered to "mine" — and the queue key
 * includes the filters, so there is no single list to invalidate. The alternative is a ticket that
 * stays visible in a queue it no longer belongs to until the 60s poll.
 */
function useTicketInvalidation() {
  const queryClient = useQueryClient();
  return (threadId?: string) => {
    if (threadId) {
      void queryClient.invalidateQueries({ queryKey: helpdeskKeys.ticket(threadId) });
    }
    void queryClient.invalidateQueries({ queryKey: ["helpdesk", "tickets"] });
    void queryClient.invalidateQueries({ queryKey: helpdeskKeys.mailboxes });
  };
}

export function useUpdateTicket() {
  const invalidate = useTicketInvalidation();
  return useMutation({
    mutationFn: (vars: { threadId: string; status?: ThreadStatus; priority?: Priority }) =>
      apiRequest(`/oneops/mail/threads?id=${vars.threadId}`, ticketSchema, {
        method: "PATCH",
        body: { status: vars.status, priority: vars.priority },
      }),
    onSuccess: (_data, vars) => invalidate(vars.threadId),
  });
}

export function useAssignTicket() {
  const invalidate = useTicketInvalidation();
  return useMutation({
    mutationFn: (vars: { threadId: string; userId?: string; teamId?: string }) =>
      apiRequestVoid(`/oneops/mail/threads/assign?id=${vars.threadId}`, {
        body: { userId: vars.userId, teamId: vars.teamId },
      }),
    onSuccess: (_data, vars) => invalidate(vars.threadId),
  });
}

export function useUnassignTicket() {
  const invalidate = useTicketInvalidation();
  return useMutation({
    mutationFn: (vars: { threadId: string }) =>
      apiRequestVoid(`/oneops/mail/threads/unassign?id=${vars.threadId}`, { method: "POST" }),
    onSuccess: (_data, vars) => invalidate(vars.threadId),
  });
}

export function useAddNote() {
  const invalidate = useTicketInvalidation();
  return useMutation({
    mutationFn: (vars: { threadId: string; bodyHtml: string }) =>
      apiRequest(`/oneops/mail/threads/notes?id=${vars.threadId}`, noteSchema, {
        body: { bodyHtml: vars.bodyHtml },
      }),
    onSuccess: (_data, vars) => invalidate(vars.threadId),
  });
}

export function useAddTicketTag() {
  const invalidate = useTicketInvalidation();
  return useMutation({
    mutationFn: (vars: { threadId: string; tagId: string }) =>
      apiRequestVoid(`/oneops/mail/threads/tags?id=${vars.threadId}`, { body: { tagId: vars.tagId } }),
    onSuccess: (_data, vars) => invalidate(vars.threadId),
  });
}

export function useRemoveTicketTag() {
  const invalidate = useTicketInvalidation();
  return useMutation({
    mutationFn: (vars: { threadId: string; tagId: string }) =>
      apiRequestVoid(`/oneops/mail/threads/tags?id=${vars.threadId}&tagId=${vars.tagId}`, { method: "DELETE" }),
    onSuccess: (_data, vars) => invalidate(vars.threadId),
  });
}

export function useReplyToTicket() {
  const invalidate = useTicketInvalidation();
  return useMutation({
    mutationFn: (vars: {
      threadId: string;
      bodyHtml: string;
      replyMode?: "REPLY" | "REPLY_ALL" | "FORWARD";
      to?: string[];
      cannedReplyId?: string;
    }) =>
      apiRequest(`/oneops/mail/threads/reply?id=${vars.threadId}`, ticketMessageSchema, {
        body: {
          replyMode: vars.replyMode ?? "REPLY",
          bodyHtml: vars.bodyHtml,
          to: vars.to,
          // Sent so the backend can count the canned reply as used. The body still goes verbatim,
          // because by the time it is sent the agent has usually edited it.
          cannedReplyId: vars.cannedReplyId,
        },
      }),
    onSuccess: (_data, vars) => invalidate(vars.threadId),
  });
}

export function useCreateTag() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (vars: { name: string; colour?: string; slug?: string }) =>
      apiRequest("/oneops/mail/tags", tagSchema, { body: vars }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: helpdeskKeys.tags }),
  });
}

export function useUpdateTag() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (vars: { tagId: string; name: string; colour?: string }) =>
      apiRequest(`/oneops/mail/tags?id=${vars.tagId}`, tagSchema, {
        method: "PATCH",
        body: { name: vars.name, colour: vars.colour },
      }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: helpdeskKeys.tags }),
  });
}

export function useDeleteTag() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (tagId: string) =>
      apiRequestVoid(`/oneops/mail/tags?id=${tagId}`, { method: "DELETE" }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: helpdeskKeys.tags }),
  });
}

export function useCreateCannedReply() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (vars: {
      title: string;
      bodyHtml: string;
      mailboxId?: string;
      shortcut?: string;
      subject?: string;
    }) => apiRequest("/oneops/mail/canned-replies", cannedReplySchema, { body: vars }),
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: helpdeskKeys.cannedReplies }),
  });
}

export function useUpdateCannedReply() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (vars: {
      replyId: string;
      title: string;
      bodyHtml: string;
      mailboxId?: string;
      shortcut?: string;
      subject?: string;
    }) =>
      apiRequest(`/oneops/mail/canned-replies?id=${vars.replyId}`, cannedReplySchema, {
        method: "PATCH",
        body: {
          title: vars.title,
          bodyHtml: vars.bodyHtml,
          mailboxId: vars.mailboxId,
          shortcut: vars.shortcut,
          subject: vars.subject,
        },
      }),
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: helpdeskKeys.cannedReplies }),
  });
}

export function useDeleteCannedReply() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (replyId: string) =>
      apiRequestVoid(`/oneops/mail/canned-replies?id=${replyId}`, { method: "DELETE" }),
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: helpdeskKeys.cannedReplies }),
  });
}

// -------------------------------------------------------------------------------------------------
// Presentation helpers
// -------------------------------------------------------------------------------------------------

/**
 * Turns a SCREAMING_CASE enum value into something readable.
 *
 * Takes a string rather than a union because statuses, priorities and event values all need the same
 * treatment, and narrowing the parameter to one of them only forces casts at the other call sites —
 * casts that would silently accept a genuinely wrong value in exchange for no safety at all.
 */
export function statusLabel(value: string): string {
  if (value === "PENDING_CUSTOMER") return "Waiting on customer";
  return value.charAt(0) + value.slice(1).toLowerCase().replace(/_/g, " ");
}

/**
 * How a ticket's SLA is doing, as something renderable.
 *
 * Returns null once a first response exists, because the SLA tracked here is first-response time and
 * a ticket that has been answered cannot breach it. The backend leaves slaDueAt set after the first
 * reply, so testing the due date alone would show a countdown on tickets that already met their
 * target.
 */
export function slaState(ticket: Ticket):
  | { tone: "breached" | "due-soon" | "ok"; label: string }
  | null {
  if (ticket.slaBreachedAt) return { tone: "breached", label: "SLA breached" };
  if (ticket.firstResponseAt || !ticket.slaDueAt) return null;

  const msLeft = new Date(ticket.slaDueAt).getTime() - Date.now();
  if (msLeft < 0) return { tone: "breached", label: "SLA overdue" };

  const minutes = Math.round(msLeft / 60_000);
  const label =
    minutes < 60
      ? `${minutes}m to first reply`
      : `${Math.round(minutes / 60)}h to first reply`;
  return { tone: minutes < 60 ? "due-soon" : "ok", label };
}
