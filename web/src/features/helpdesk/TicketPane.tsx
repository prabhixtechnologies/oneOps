import type { ReactNode } from "react";
import { useMemo, useState, useEffect, useRef } from "react";
import {
  AlertTriangle,
  ArrowDownLeft,
  ArrowUpRight,
  Check,
  Loader2,
  Plus,
  Send,
  StickyNote,
  Tag as TagIcon,
  X,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { EmptyState } from "@/components/shared/states";
import { Skeleton } from "@/components/ui/skeleton";
import { getApiErrorMessage } from "@/lib/api-client";
import { useAuth } from "@/lib/auth";
import {
  priorities,
  slaState,
  statusLabel,
  useAddNote,
  useAddTicketTag,
  useAssignableMembers,
  useAssignTicket,
  useCannedReplies,
  useRemoveTicketTag,
  useReplyToTicket,
  useTags,
  useTicket,
  useUnassignTicket,
  useUpdateTicket,
  workflowStatuses,
  type Priority,
  type ThreadStatus,
  type TicketEvent,
} from "./api";
import { hardenLinks, sanitizeEmailHtml } from "./sanitize";
import { cn, initials } from "@/lib/utils";

/**
 * One ticket: the conversation, and everything an agent does to it.
 *
 * The controls sit above the conversation rather than in a side rail, because the decisions — whose is
 * this, how urgent, what state — are what an agent came here to make, and a rail is where they go to
 * be missed on a laptop screen.
 */
export function TicketPane({
  threadId,
  onClose,
}: {
  threadId: string;
  onClose: () => void;
}) {
  const { me } = useAuth();
  const detail = useTicket(threadId);
  const tags = useTags();
  const members = useAssignableMembers(me?.organizationId ?? undefined);
  const cannedReplies = useCannedReplies();

  const update = useUpdateTicket();
  const assign = useAssignTicket();
  const unassign = useUnassignTicket();
  const addTag = useAddTicketTag();
  const removeTag = useRemoveTicketTag();
  const addNote = useAddNote();
  const reply = useReplyToTicket();

  const [replyBody, setReplyBody] = useState("");
  const [noteBody, setNoteBody] = useState("");
  const [usedCannedReplyId, setUsedCannedReplyId] = useState<string>();
  const [tab, setTab] = useState<"reply" | "note">("reply");
  const [actionError, setActionError] = useState<string>();

  const ticket = detail.data?.thread;

  // Conversation and internal activity woven into one list, sorted by time. Reading them separately
  // loses the ordering that matters: a status change three messages ago means something different
  // from the same change just now.
  const timeline = useMemo(() => {
    if (!detail.data) return [];
    const messages = detail.data.messages.map((m) => ({
      kind: "message" as const,
      at: m.occurredAt,
      message: m,
    }));
    const notes = detail.data.notes.map((n) => ({
      kind: "note" as const,
      at: n.createdAt,
      note: n,
    }));
    const events = detail.data.events
      // MESSAGE_* duplicate the messages already in the list, and NOTE_ADDED duplicates the notes.
      .filter(
        (e) =>
          e.eventType !== "MESSAGE_RECEIVED" &&
          e.eventType !== "MESSAGE_SENT" &&
          e.eventType !== "NOTE_ADDED",
      )
      .map((e) => ({ kind: "event" as const, at: e.createdAt, event: e }));
    return [...messages, ...notes, ...events].sort((a, b) => a.at.localeCompare(b.at));
  }, [detail.data]);

  const run = async (label: string, action: () => Promise<unknown>) => {
    setActionError(undefined);
    try {
      await action();
    } catch (err) {
      setActionError(`${label} failed: ${getApiErrorMessage(err)}`);
    }
  };

  if (detail.isPending) {
    return (
      <div className="flex-1 space-y-3 p-6">
        <Skeleton className="h-8 w-2/3" />
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-24 w-full" />
      </div>
    );
  }

  if (detail.isError || !ticket) {
    return (
      <EmptyState
        icon={<AlertTriangle className="size-8" />}
        title="This ticket could not be loaded"
        description={detail.error ? getApiErrorMessage(detail.error) : undefined}
      />
    );
  }

  const sla = slaState(ticket);
  const assignedToMe = ticket.assigneeUserId === me?.userId;
  const busy =
    update.isPending || assign.isPending || unassign.isPending || reply.isPending;

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <header className="border-b border-border px-6 py-4">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h2 className="truncate text-base font-semibold">
              {ticket.subject || "(no subject)"}
            </h2>
            <p className="mt-0.5 text-xs text-text-muted">
              <span className="font-mono">{ticket.referenceKey}</span>
              {ticket.customerEmail ? ` · ${ticket.customerEmail}` : ""}
              {` · ${ticket.messageCount} message${ticket.messageCount === 1 ? "" : "s"}`}
            </p>
          </div>
          <Button variant="ghost" size="icon" aria-label="Close ticket" onClick={onClose}>
            <X className="size-4" />
          </Button>
        </div>

        <div className="mt-3 flex flex-wrap items-center gap-2">
          <select
            aria-label="Status"
            value={ticket.status}
            disabled={busy}
            onChange={(e) =>
              void run("Changing status", () =>
                update.mutateAsync({ threadId, status: e.target.value as ThreadStatus }),
              )
            }
            className="min-h-11 rounded-md border border-border bg-surface px-3 py-2 text-sm"
          >
            {workflowStatuses.map((s) => (
              <option key={s} value={s}>
                {statusLabel(s)}
              </option>
            ))}
            {/* Present only when already set, so an agent cannot file a ticket as spam by fumbling a
                dropdown, but a ticket that is spam still shows what it is. */}
            {!workflowStatuses.includes(ticket.status) ? (
              <option value={ticket.status}>{statusLabel(ticket.status)}</option>
            ) : null}
          </select>

          <select
            aria-label="Priority"
            value={ticket.priority}
            disabled={busy}
            onChange={(e) =>
              void run("Changing priority", () =>
                update.mutateAsync({ threadId, priority: e.target.value as Priority }),
              )
            }
            className="min-h-11 rounded-md border border-border bg-surface px-3 py-2 text-sm"
          >
            {priorities.map((p) => (
              <option key={p} value={p}>
                {statusLabel(p)}
              </option>
            ))}
          </select>

          {members.data ? (
            <select
              aria-label="Assignee"
              value={ticket.assigneeUserId ?? ""}
              disabled={busy}
              onChange={(e) =>
                void run("Assigning", () =>
                  e.target.value
                    ? assign.mutateAsync({ threadId, userId: e.target.value })
                    : unassign.mutateAsync({ threadId }),
                )
              }
              className="min-h-11 rounded-md border border-border bg-surface px-3 py-2 text-sm"
            >
              <option value="">Unassigned</option>
              {members.data.items.map((m) => (
                <option key={m.userId} value={m.userId}>
                  {m.displayName || m.email || m.userId}
                </option>
              ))}
            </select>
          ) : (
            /* The members endpoint needs ORG_MEMBER_READ, which an agent working a queue need not
               have. Claiming it yourself is the common action anyway, so its absence costs little. */
            <Button
              variant={assignedToMe ? "secondary" : "outline"}
              size="sm"
              disabled={busy}
              onClick={() =>
                void run("Assigning", () =>
                  assignedToMe
                    ? unassign.mutateAsync({ threadId })
                    : assign.mutateAsync({ threadId, userId: me?.userId }),
                )
              }
            >
              {assignedToMe ? "Release" : "Assign to me"}
            </Button>
          )}

          {sla ? (
            <span
              className={cn(
                "text-xs",
                sla.tone === "breached"
                  ? "text-destructive"
                  : sla.tone === "due-soon"
                    ? "text-warning"
                    : "text-text-muted",
              )}
            >
              {sla.label}
            </span>
          ) : ticket.firstResponseAt ? (
            <span className="inline-flex items-center gap-1 text-xs text-text-muted">
              <Check className="size-3" />
              Answered
            </span>
          ) : null}
        </div>

        <div className="mt-2 flex flex-wrap items-center gap-1.5">
          <TagIcon className="size-3 text-text-muted" />
          {ticket.tags.map((tag) => (
            <span
              key={tag.id}
              className="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium"
              style={{ backgroundColor: `${tag.colour}26`, color: tag.colour }}
            >
              {tag.name}
              <button
                type="button"
                aria-label={`Remove tag ${tag.name}`}
                onClick={() =>
                  void run("Removing tag", () =>
                    removeTag.mutateAsync({ threadId, tagId: tag.id }),
                  )
                }
                className="opacity-70 hover:opacity-100"
              >
                <X className="size-2.5" />
              </button>
            </span>
          ))}
          {(tags.data ?? []).some((t) => !ticket.tags.some((a) => a.id === t.id)) ? (
            <select
              aria-label="Add a tag"
              value=""
              onChange={(e) => {
                if (!e.target.value) return;
                void run("Adding tag", () =>
                  addTag.mutateAsync({ threadId, tagId: e.target.value }),
                );
              }}
              className="rounded-full border border-dashed border-border bg-transparent px-2 py-0.5 text-[11px] text-text-muted"
            >
              <option value="">
                {ticket.tags.length === 0 ? "Add a tag" : "Add"}
              </option>
              {(tags.data ?? [])
                .filter((t) => !ticket.tags.some((a) => a.id === t.id))
                .map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
            </select>
          ) : null}
        </div>

        {actionError ? (
          <p role="alert" className="mt-2 text-xs text-destructive">
            {actionError}
          </p>
        ) : null}
      </header>

      <div className="min-h-0 flex-1 space-y-3 overflow-y-auto px-6 py-4">
        {timeline.map((entry, i) => {
          if (entry.kind === "message") {
            const m = entry.message;
            const outbound = m.direction === "OUTBOUND";
            return (
              <article
                key={m.id}
                className={cn(
                  "rounded-lg border p-4",
                  outbound ? "border-primary/30 bg-primary/5" : "border-border bg-surface",
                )}
              >
                <div className="mb-2 flex items-center gap-2 text-xs text-text-muted">
                  <Avatar>
                    <AvatarFallback>{initials(m.fromName || m.fromAddress || "?")}</AvatarFallback>
                  </Avatar>
                  {outbound ? (
                    <ArrowUpRight className="size-3" />
                  ) : (
                    <ArrowDownLeft className="size-3" />
                  )}
                  <span className="truncate font-medium text-text">
                    {m.fromName || m.fromAddress || (outbound ? "You" : "Customer")}
                  </span>
                  <span className="ml-auto shrink-0">
                    {new Date(m.occurredAt).toLocaleString()}
                  </span>
                </div>
                {m.bodyHtml ? (
                  <SanitizedMailHtml
                    className="prose-sm max-w-none text-sm [&_a]:text-primary [&_a]:underline"
                    html={m.bodyHtml}
                  />
                ) : (
                  <p className="whitespace-pre-wrap text-sm">{m.bodyText ?? "(empty message)"}</p>
                )}
              </article>
            );
          }

          if (entry.kind === "note") {
            return (
              <article
                key={entry.note.id}
                className="rounded-lg border border-amber-500/30 bg-amber-500/5 p-4"
              >
                <div className="mb-1 flex items-center gap-2 text-xs text-warning">
                  <StickyNote className="size-3" />
                  <span className="font-medium">Internal note</span>
                  <span className="ml-auto text-text-muted">
                    {new Date(entry.note.createdAt).toLocaleString()}
                  </span>
                </div>
                <SanitizedMailHtml
                  className="prose-sm max-w-none text-sm"
                  html={entry.note.bodyHtml}
                />
              </article>
            );
          }

          return (
            <p
              key={`${entry.event.eventType}-${entry.at}-${i}`}
              className="px-1 text-xs text-text-muted"
            >
              {describeEvent(entry.event)} ·{" "}
              {new Date(entry.event.createdAt).toLocaleString()}
            </p>
          );
        })}
      </div>

      <footer className="border-t border-border px-6 py-3">
        <div className="mb-2 flex items-center gap-1">
          <TabButton active={tab === "reply"} onClick={() => setTab("reply")}>
            Reply to customer
          </TabButton>
          <TabButton active={tab === "note"} onClick={() => setTab("note")}>
            Internal note
          </TabButton>
        </div>

        {tab === "reply" ? (
          <div className="space-y-2">
            {(cannedReplies.data ?? []).length > 0 ? (
              <select
                aria-label="Insert a canned reply"
                value=""
                onChange={(e) => {
                  const chosen = cannedReplies.data?.find((c) => c.id === e.target.value);
                  if (!chosen) return;
                  // Appended rather than replacing, so an agent who has already typed does not lose it.
                  setReplyBody((body) => (body ? `${body}\n\n${chosen.bodyHtml}` : chosen.bodyHtml));
                  setUsedCannedReplyId(chosen.id);
                }}
                className="min-h-11 rounded-md border border-border bg-surface px-3 py-2 text-sm"
              >
                <option value="">Insert a canned reply…</option>
                {cannedReplies.data?.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.shortcut ? `${c.shortcut} — ${c.title}` : c.title}
                  </option>
                ))}
              </select>
            ) : null}
            <Textarea
              value={replyBody}
              onChange={(e) => setReplyBody(e.target.value)}
              rows={4}
              placeholder={`Reply to ${ticket.customerEmail ?? "the customer"}…`}
              aria-label="Reply body"
              data-testid="mail-reply-body"
            />
            <div className="flex items-center justify-between gap-2">
              <p className="text-xs text-text-muted">
                Sent from the mailbox, and it claims the ticket if nobody has.
              </p>
              <Button
                size="sm"
                disabled={!replyBody.trim() || reply.isPending}
                data-testid="mail-reply-send"
                onClick={() =>
                  void run("Sending the reply", async () => {
                    await reply.mutateAsync({
                      threadId,
                      bodyHtml: replyBody,
                      cannedReplyId: usedCannedReplyId,
                    });
                    setReplyBody("");
                    setUsedCannedReplyId(undefined);
                  })
                }
              >
                {reply.isPending ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  <Send className="size-4" />
                )}
                Send
              </Button>
            </div>
          </div>
        ) : (
          <div className="space-y-2">
            <Textarea
              value={noteBody}
              onChange={(e) => setNoteBody(e.target.value)}
              rows={3}
              placeholder="Visible to your team only. The customer never sees this."
              aria-label="Note body"
            />
            <div className="flex justify-end">
              <Button
                size="sm"
                variant="secondary"
                disabled={!noteBody.trim() || addNote.isPending}
                onClick={() =>
                  void run("Adding the note", async () => {
                    await addNote.mutateAsync({ threadId, bodyHtml: noteBody });
                    setNoteBody("");
                  })
                }
              >
                {addNote.isPending ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  <Plus className="size-4" />
                )}
                Add note
              </Button>
            </div>
          </div>
        )}
      </footer>
    </div>
  );
}

function TabButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "rounded-md px-2.5 py-1 text-xs font-medium transition-colors",
        active ? "bg-surface-muted text-text" : "text-text-muted hover:bg-surface-muted/60",
      )}
    >
      {children}
    </button>
  );
}

function describeEvent(event: TicketEvent): string {
  const who = event.actorLabel ? `${event.actorLabel} ` : "";
  switch (event.eventType) {
    case "CREATED":
      return "Ticket created";
    case "ASSIGNED":
      return `${who}assigned this ticket`;
    case "UNASSIGNED":
      return `${who}unassigned this ticket`;
    case "STATUS_CHANGED":
      return `${who}changed status${event.fromValue ? ` from ${statusLabel(event.fromValue)}` : ""}${
        event.toValue ? ` to ${statusLabel(event.toValue)}` : ""
      }`;
    case "PRIORITY_CHANGED":
      return `${who}changed priority${event.toValue ? ` to ${statusLabel(event.toValue)}` : ""}`;
    case "TAG_ADDED":
      return `${who}added a tag`;
    case "TAG_REMOVED":
      return `${who}removed a tag`;
    case "SLA_BREACHED":
      return "First-response SLA breached";
    case "AUTO_REPLIED":
      return "An automatic reply was sent";
    case "RULE_APPLIED":
      return "A routing rule was applied";
    case "MOVED":
      return "Moved between mailboxes";
    case "MERGED":
      return "Merged with another ticket";
    default:
      return event.eventType.toLowerCase().replace(/_/g, " ");
  }
}

function SanitizedMailHtml({ html, className }: { html: string; className?: string }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (ref.current) hardenLinks(ref.current);
  }, [html]);
  return (
    <div
      ref={ref}
      className={className}
      // Sanitised, never raw: this is attacker-controlled HTML from an inbound email.
      dangerouslySetInnerHTML={{ __html: sanitizeEmailHtml(html) }}
    />
  );
}

