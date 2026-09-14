import { useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { AlertTriangle, Filter, Inbox, RefreshCw, Search, Settings, X } from "lucide-react";

import { EmptyState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { useAuth } from "@/lib/auth";
import { cn } from "@/lib/utils";
import { PERMISSION_MAILBOX_MANAGE, PERMISSION_THREAD_UPDATE } from "./mailbox-admin";
import {
  priorities,
  slaState,
  statusLabel,
  useHelpdeskMailboxes,
  useTags,
  useTickets,
  workflowStatuses,
  type Priority,
  type ThreadStatus,
  type Ticket,
  type TicketFilters,
} from "./api";
import { TicketPane } from "./TicketPane";

/**
 * The shared-mailbox queue: what work is outstanding, and whose it is.
 *
 * Routed rather than held in component state, unlike the personal mail client next door. A ticket is
 * something people send each other links to — "can you look at this one" — and a queue whose
 * selection lives in useState has no URL to send. The filters stay in state, because a filter is how
 * one person is working right now and is not worth sharing.
 */
export default function InboxPage() {
  const { threadId } = useParams<{ threadId?: string }>();
  const navigate = useNavigate();
  const { me, permissions } = useAuth();

  // The two settings permissions land on different sections, so the link has to point at one the
  // caller can actually open rather than always at the mailbox list.
  const canManageMailboxes = permissions.includes(PERMISSION_MAILBOX_MANAGE);
  const canManageVocabulary = permissions.includes(PERMISSION_THREAD_UPDATE);
  const canReachSettings = canManageMailboxes || canManageVocabulary;
  const settingsHome = canManageMailboxes ? "/settings/mail" : "/settings/mail/tags";

  const [mailboxId, setMailboxId] = useState<string>();
  const [status, setStatus] = useState<ThreadStatus | undefined>("OPEN");
  const [priority, setPriority] = useState<Priority>();
  const [tagId, setTagId] = useState<string>();
  const [mineOnly, setMineOnly] = useState(false);
  const [search, setSearch] = useState("");
  const [submittedSearch, setSubmittedSearch] = useState("");

  const filters: TicketFilters = useMemo(
    () => ({
      mailboxId,
      status,
      priority,
      tagId,
      assigneeUserId: mineOnly ? me?.userId : undefined,
      q: submittedSearch || undefined,
    }),
    [mailboxId, status, priority, tagId, mineOnly, me?.userId, submittedSearch],
  );

  const mailboxes = useHelpdeskMailboxes();
  const tags = useTags();
  const tickets = useTickets(filters);

  const rows = useMemo(
    () => tickets.data?.pages.flatMap((p) => p.items) ?? [],
    [tickets.data],
  );

  const activeFilterCount =
    (mailboxId ? 1 : 0) +
    (status ? 1 : 0) +
    (priority ? 1 : 0) +
    (tagId ? 1 : 0) +
    (mineOnly ? 1 : 0);

  return (
    <div className="flex h-[calc(100dvh-3.5rem)] min-h-0 min-w-0 overflow-x-clip pb-[env(safe-area-inset-bottom)]">
      <section
        className={cn(
          "min-w-0 flex-col border-r border-border",
          threadId ? "hidden md:flex md:w-[420px] md:shrink-0" : "flex w-full md:w-[420px] md:shrink-0",
        )}
      >
        <header className="flex flex-col gap-3 border-b border-border px-4 py-3">
          <div className="flex items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <h1 className="font-display text-xl font-semibold tracking-tight">Inbox</h1>
              {activeFilterCount > 0 ? (
                <Badge variant="secondary">
                  <Filter className="mr-1 size-3" />
                  {activeFilterCount}
                </Badge>
              ) : null}
            </div>
            <div className="flex items-center gap-1">
              {canReachSettings ? (
                <Link
                  to={settingsHome}
                  className="inline-flex min-h-11 items-center rounded-md px-3 text-sm text-text-muted hover:bg-surface-muted"
                >
                  <Settings className="mr-1 inline size-3" />
                  Settings
                </Link>
              ) : null}
              <Button
                variant="ghost"
                size="icon"
                aria-label="Refresh the queue"
                onClick={() => void tickets.refetch()}
              >
                <RefreshCw className={cn("size-4", tickets.isFetching && "animate-spin")} />
              </Button>
            </div>
          </div>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              setSubmittedSearch(search.trim());
            }}
            className="relative"
          >
            <Search className="pointer-events-none absolute left-2 top-1/2 size-4 -translate-y-1/2 text-text-muted" />
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search subject, sender or body"
              className="pl-8"
              aria-label="Search tickets"
            />
            {submittedSearch ? (
              <button
                type="button"
                aria-label="Clear search"
                onClick={() => {
                  setSearch("");
                  setSubmittedSearch("");
                }}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-text-muted hover:text-text"
              >
                <X className="size-4" />
              </button>
            ) : null}
          </form>

          <div className="flex flex-wrap gap-1.5">
            <FilterSelect
              label="Mailbox"
              value={mailboxId ?? ""}
              onChange={(v) => setMailboxId(v || undefined)}
              options={[
                { value: "", label: "All mailboxes" },
                ...(mailboxes.data ?? []).map((m) => ({
                  value: m.id,
                  // The open count is the reason to pick one mailbox over another, so it belongs in
                  // the option rather than a tooltip nobody opens.
                  label: `${m.name} (${m.openThreadCount})`,
                })),
              ]}
            />
            <FilterSelect
              label="Status"
              value={status ?? ""}
              onChange={(v) => setStatus((v || undefined) as ThreadStatus | undefined)}
              options={[
                { value: "", label: "Any status" },
                ...workflowStatuses.map((s) => ({ value: s, label: statusLabel(s) })),
              ]}
            />
            <FilterSelect
              label="Priority"
              value={priority ?? ""}
              onChange={(v) => setPriority((v || undefined) as Priority | undefined)}
              options={[
                { value: "", label: "Any priority" },
                ...priorities.map((p) => ({ value: p, label: statusLabel(p) })),
              ]}
            />
            {(tags.data ?? []).length > 0 ? (
              <FilterSelect
                label="Tag"
                value={tagId ?? ""}
                onChange={(v) => setTagId(v || undefined)}
                options={[
                  { value: "", label: "Any tag" },
                  ...(tags.data ?? []).map((t) => ({ value: t.id, label: t.name })),
                ]}
              />
            ) : null}
            <button
              type="button"
              onClick={() => setMineOnly((v) => !v)}
              className={cn(
                "rounded-md border px-2 py-1 text-xs transition-colors",
                mineOnly
                  ? "border-primary bg-primary text-primary-foreground"
                  : "border-border text-text-muted hover:bg-surface-muted",
              )}
            >
              Assigned to me
            </button>
          </div>
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto" tabIndex={0} aria-label="Ticket list">
          {tickets.isPending ? (
            <div className="space-y-2 p-3">
              {[0, 1, 2, 3, 4].map((i) => (
                <Skeleton key={i} className="h-16 w-full" />
              ))}
            </div>
          ) : tickets.isError ? (
            <EmptyState
              icon={<AlertTriangle className="size-8" />}
              title="The inbox could not be loaded"
              description="This is usually a lost connection rather than an empty queue. Try refreshing."
            />
          ) : rows.length === 0 ? (
            <EmptyState
              icon={<Inbox className="size-8" />}
              title={activeFilterCount > 0 ? "Nothing matches these filters" : "The inbox is clear"}
              description={
                activeFilterCount > 0
                  ? "Widen the filters to see more."
                  : "New mail to a shared mailbox appears here."
              }
            />
          ) : (
            <ul>
              {rows.map((ticket) => (
                <li key={ticket.id} className="scan-row">
                  <TicketRow
                    ticket={ticket}
                    selected={ticket.id === threadId}
                    mineUserId={me?.userId}
                    onSelect={() => navigate(`/inbox/${ticket.id}`)}
                  />
                </li>
              ))}
              {tickets.hasNextPage ? (
                <li className="p-3">
                  <Button
                    variant="outline"
                    className="w-full"
                    disabled={tickets.isFetchingNextPage}
                    onClick={() => void tickets.fetchNextPage()}
                  >
                    {tickets.isFetchingNextPage ? "Loading…" : "Load more"}
                  </Button>
                </li>
              ) : null}
            </ul>
          )}
        </div>
      </section>

      <section className={cn("min-w-0 flex-1", threadId ? "flex" : "hidden md:flex")}>
        {threadId ? (
          <TicketPane threadId={threadId} onClose={() => navigate("/inbox")} />
        ) : (
          <EmptyState
            icon={<Inbox className="size-8" />}
            title="No ticket selected"
            description="Pick one from the inbox to read it, reply, or hand it to someone."
          />
        )}
      </section>
    </div>
  );
}

function FilterSelect({
  label,
  value,
  onChange,
  options,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: { value: string; label: string }[];
}) {
  return (
    <select
      aria-label={label}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className={cn(
        "rounded-md border px-2 py-1 text-xs transition-colors",
        value
          ? "border-primary bg-primary text-primary-foreground"
          : "border-border bg-surface text-text-muted hover:bg-surface-muted",
      )}
    >
      {options.map((o) => (
        <option key={o.value} value={o.value} className="bg-surface text-text">
          {o.label}
        </option>
      ))}
    </select>
  );
}

const priorityTone: Record<Priority, string> = {
  LOW: "text-text-muted",
  NORMAL: "text-text-muted",
  HIGH: "text-warning",
  URGENT: "text-destructive",
};

function TicketRow({
  ticket,
  selected,
  mineUserId,
  onSelect,
}: {
  ticket: Ticket;
  selected: boolean;
  mineUserId?: string;
  onSelect: () => void;
}) {
  const sla = slaState(ticket);
  const unread = ticket.unreadCount > 0;

  return (
    <button
      type="button"
      onClick={onSelect}
      aria-current={selected}
      data-testid="inbox-ticket-row"
      data-thread-id={ticket.id}
      className={cn(
        "flex w-full flex-col gap-1 border-b border-border px-4 py-3 text-left transition-colors",
        selected ? "bg-primary/10" : "hover:bg-surface-muted",
      )}
    >
      <div className="flex items-baseline justify-between gap-2">
        <span
          className={cn(
            "truncate text-sm",
            unread ? "font-semibold text-text" : "text-text-muted",
          )}
        >
          {ticket.customerEmail ?? "Unknown sender"}
        </span>
        <span className="shrink-0 text-[11px] text-text-muted">
          {relativeTime(ticket.lastMessageAt)}
        </span>
      </div>

      <span className={cn("truncate text-sm", unread ? "text-text" : "text-text-muted")}>
        {ticket.subject || "(no subject)"}
      </span>

      <div className="flex flex-wrap items-center gap-1.5">
        <span className="font-mono text-[10px] text-text-muted">{ticket.referenceKey}</span>

        {ticket.priority !== "NORMAL" ? (
          <span className={cn("text-[11px] font-medium", priorityTone[ticket.priority])}>
            {statusLabel(ticket.priority)}
          </span>
        ) : null}

        {ticket.status !== "OPEN" ? (
          <Badge variant="secondary">{statusLabel(ticket.status)}</Badge>
        ) : null}

        {/* Unassigned is the state worth surfacing in a queue: an assigned ticket has an owner who
            will see it, and an unassigned one is what nobody has picked up yet. */}
        {!ticket.assigneeUserId && !ticket.assigneeTeamId ? (
          <Badge variant="outline">Unassigned</Badge>
        ) : ticket.assigneeUserId === mineUserId ? (
          <Badge>Mine</Badge>
        ) : null}

        {sla ? (
          <span
            className={cn(
              "text-[11px]",
              sla.tone === "breached"
                ? "text-destructive"
                : sla.tone === "due-soon"
                  ? "text-warning"
                  : "text-text-muted",
            )}
          >
            {sla.label}
          </span>
        ) : null}

        {ticket.tags.map((tag) => (
          <span
            key={tag.id}
            className="rounded-full px-1.5 py-0.5 text-[10px] font-medium"
            style={{ backgroundColor: `${tag.colour}26`, color: tag.colour }}
          >
            {tag.name}
          </span>
        ))}
      </div>
    </button>
  );
}

/**
 * Kept local and deliberately coarse.
 *
 * A queue is scanned, not read, so "3h" carries the information and a precise timestamp is noise. The
 * exact time is on the message in the ticket pane, where somebody is actually reading.
 */
function relativeTime(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const minutes = Math.round(diffMs / 60_000);
  if (minutes < 1) return "now";
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h`;
  const days = Math.round(hours / 24);
  return days < 7 ? `${days}d` : new Date(iso).toLocaleDateString();
}
