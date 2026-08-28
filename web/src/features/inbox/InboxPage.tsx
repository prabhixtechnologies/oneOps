import {
  Archive,
  ArrowLeft,
  Filter,
  HelpCircle,
  Search,
  UserPlus,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router";
import { toast } from "sonner";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Separator } from "@/components/ui/separator";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { CursorList } from "@/components/shared/CursorList";
import { CollapsibleQuotedText, SanitizedEmailHtml } from "@/components/shared/email-content";
import { RelativeTime } from "@/components/shared/RelativeTime";
import { ErrorState } from "@/components/shared/states";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { connectMailStream } from "@/lib/sse";
import type { MailStreamEvent, ThreadSummary } from "@/lib/schemas/mail";
import { eventDetail, isSlaBreached, isThreadUnread } from "@/lib/schemas/mail";
import {
  useAssignThread,
  useBulkUpdateThreads,
  useCannedReplies,
  useMailboxes,
  useReplyToThread,
  useThread,
  useThreads,
  useUpdateThread,
  useUploadFile,
} from "@/features/mail/api";
import { useMembers } from "@/features/org/api";
import { useAuth } from "@/lib/auth";
import { PERMISSIONS } from "@/lib/permissions";
import { cn } from "@/lib/utils";
import { Skeleton } from "@/components/ui/skeleton";
import { getApiErrorMessage } from "@/lib/api-client";
import { ReplyComposer } from "@/features/inbox/ReplyComposer";
import { MailThreadAiPanel } from "@/features/ai/components/MailThreadAiPanel";

const SHORTCUTS = [
  { keys: "j / k", action: "Navigate threads" },
  { keys: "e", action: "Archive thread" },
  { keys: "a", action: "Assign to me" },
  { keys: "r", action: "Reply" },
  { keys: "/", action: "Focus search" },
  { keys: "?", action: "Show shortcuts" },
];

function SlaBadge({ dueAt, breached }: { dueAt: string | null | undefined; breached: boolean }) {
  if (!dueAt) return null;
  return (
    <Badge variant={breached ? "destructive" : "warning"} className="text-[10px]">
      {breached
        ? "SLA breached"
        : `Due ${new Date(dueAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`}
    </Badge>
  );
}

function ThreadListRow({
  thread,
  active,
  checked,
  onSelect,
  onCheck,
}: {
  thread: ThreadSummary;
  active: boolean;
  checked: boolean;
  onSelect: () => void;
  onCheck: (checked: boolean) => void;
}) {
  const unread = isThreadUnread(thread);
  return (
    <div
      className={cn(
        "flex cursor-pointer items-start gap-2 border-b border-border px-3 py-2.5 text-sm hover:bg-surface-muted/50",
        active && "border-l-2 border-l-primary bg-primary/5",
      )}
      onClick={onSelect}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => e.key === "Enter" && onSelect()}
    >
      <Checkbox
        checked={checked}
        onCheckedChange={(c) => onCheck(!!c)}
        onClick={(e) => e.stopPropagation()}
        aria-label={`Select ${thread.subject}`}
      />
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          {unread && <span className="h-2 w-2 shrink-0 rounded-full bg-primary" aria-label="Unread" />}
          <span className={cn("truncate font-medium", unread && "text-text")}>{thread.subject}</span>
          {thread.priority === "URGENT" && (
            <Badge variant="destructive" className="text-[10px]">Urgent</Badge>
          )}
        </div>
        <p className="truncate text-xs text-text-muted">{thread.snippet}</p>
        <div className="mt-1 flex items-center gap-2">
          {thread.customerEmail && (
            <span className="break-all text-[10px] text-text-muted">{thread.customerEmail}</span>
          )}
          <RelativeTime date={thread.lastMessageAt} className="text-[10px]" />
          <SlaBadge dueAt={thread.slaDueAt} breached={isSlaBreached(thread)} />
        </div>
      </div>
    </div>
  );
}

function FilterPanel({
  mailboxId,
  setMailboxId,
  viewId,
  setViewId,
  savedViews,
  mailboxes,
  loading,
}: {
  mailboxId: string | undefined;
  setMailboxId: (id: string | undefined) => void;
  viewId: string;
  setViewId: (id: string) => void;
  savedViews: { id: string; label: string }[];
  mailboxes: { id: string; name: string; openThreadCount: number }[];
  loading: boolean;
}) {
  return (
    <>
      <div className="p-3">
        <p className="mb-2 text-xs font-medium uppercase tracking-wide text-text-muted">Mailboxes</p>
        <button
          type="button"
          className={cn("w-full rounded-md px-2 py-1.5 text-left text-sm", !mailboxId && "bg-surface font-medium")}
          onClick={() => setMailboxId(undefined)}
        >
          All inboxes
        </button>
        {loading ? (
          <Skeleton className="mt-2 h-8" />
        ) : (
          mailboxes.map((mb) => (
            <button
              key={mb.id}
              type="button"
              className={cn(
                "mt-0.5 flex w-full items-center justify-between rounded-md px-2 py-1.5 text-left text-sm hover:bg-surface",
                mailboxId === mb.id && "bg-surface font-medium",
              )}
              onClick={() => setMailboxId(mb.id)}
            >
              <span className="truncate">{mb.name}</span>
              <Badge variant="secondary" className="text-[10px]">{mb.openThreadCount}</Badge>
            </button>
          ))
        )}
      </div>
      <Separator />
      <div className="p-3">
        <p className="mb-2 text-xs font-medium uppercase tracking-wide text-text-muted">Saved views</p>
        {savedViews.map((v) => (
          <button
            key={v.id}
            type="button"
            className={cn(
              "w-full rounded-md px-2 py-1.5 text-left text-sm hover:bg-surface",
              viewId === v.id && "bg-surface font-medium",
            )}
            onClick={() => setViewId(v.id)}
          >
            {v.label}
          </button>
        ))}
      </div>
    </>
  );
}

export default function InboxPage() {
  const { userId } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const [mailboxId, setMailboxId] = useState<string | undefined>();
  const [viewId, setViewId] = useState("all");
  const [search, setSearch] = useState("");
  const [selectedThreadId, setSelectedThreadId] = useState<string | undefined>();
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [showShortcuts, setShowShortcuts] = useState(false);
  const [filterOpen, setFilterOpen] = useState(false);
  const [assignOpen, setAssignOpen] = useState(false);
  const [focusedIndex, setFocusedIndex] = useState(0);
  const [mobileShowDetail, setMobileShowDetail] = useState(false);

  const savedViews = useMemo(
    () => [
      { id: "all", label: "All open", filters: { status: "OPEN" } as Record<string, string> },
      {
        id: "mine",
        label: "Assigned to me",
        filters: userId ? { assigneeUserId: userId } : {},
      },
      { id: "unread", label: "Unread", filters: { unreadOnly: "true" } },
      { id: "urgent", label: "Urgent", filters: { priority: "URGENT" } },
    ],
    [userId],
  );

  const viewFilters = savedViews.find((v) => v.id === viewId)?.filters ?? {};
  const filters = useMemo(
    () => ({ ...viewFilters, mailboxId, q: search || undefined }),
    [viewFilters, mailboxId, search],
  );

  const mailboxesQuery = useMailboxes();
  const threadsQuery = useThreads(filters);
  const threadQuery = useThread(selectedThreadId);
  const cannedQuery = useCannedReplies();
  const membersQuery = useMembers("");
  const replyMutation = useReplyToThread();
  const updateMutation = useUpdateThread();
  const bulkMutation = useBulkUpdateThreads();
  const assignMutation = useAssignThread();
  const uploadMutation = useUploadFile();

  const threads = useMemo(
    () => threadsQuery.data?.pages.flatMap((p) => p.items) ?? [],
    [threadsQuery.data],
  );

  const mailboxes = mailboxesQuery.data ?? [];
  const thread = threadQuery.data?.thread;
  const messages = threadQuery.data?.messages ?? [];
  const notes = threadQuery.data?.notes ?? [];
  const events = threadQuery.data?.events ?? [];

  useEffect(() => {
    if (searchParams.get("compose") === "true") {
      setMobileShowDetail(true);
      searchParams.delete("compose");
      setSearchParams(searchParams, { replace: true });
      setTimeout(() => document.getElementById("composer")?.focus(), 100);
    }
  }, [searchParams, setSearchParams]);

  useEffect(() => {
    if (threads.length > 0 && !selectedThreadId) {
      setSelectedThreadId(threads[0].id);
    }
  }, [threads, selectedThreadId]);

  useEffect(() => {
    return connectMailStream((event: MailStreamEvent) => {
      if (event.event === "thread.updated" || event.event === "thread.new") {
        void threadsQuery.refetch();
      }
    });
  }, [threadsQuery]);

  const selectThread = (id: string) => {
    setSelectedThreadId(id);
    setMobileShowDetail(true);
  };

  const handleKeyDown = useCallback(
    (e: KeyboardEvent) => {
      if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) return;
      if (e.key === "?") {
        setShowShortcuts(true);
        return;
      }
      if (e.key === "/") {
        e.preventDefault();
        document.getElementById("inbox-search")?.focus();
        return;
      }
      if (e.key === "j") {
        setFocusedIndex((i) => Math.min(i + 1, threads.length - 1));
        return;
      }
      if (e.key === "k") {
        setFocusedIndex((i) => Math.max(i - 1, 0));
        return;
      }
      if (e.key === "e" && selectedThreadId) {
        void updateMutation
          .mutateAsync({ threadId: selectedThreadId, data: { status: "CLOSED" } })
          .then(() => toast.success("Thread archived"))
          .catch((err) => toast.error(getApiErrorMessage(err)));
      }
      if (e.key === "a" && selectedThreadId && userId) {
        void assignMutation
          .mutateAsync({ threadId: selectedThreadId, userId })
          .then(() => toast.success("Assigned to you"))
          .catch((err) => toast.error(getApiErrorMessage(err)));
      }
      if (e.key === "r" && selectedThreadId) {
        document.getElementById("composer")?.focus();
      }
    },
    [threads.length, selectedThreadId, userId, updateMutation, assignMutation],
  );

  useEffect(() => {
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [handleKeyDown]);

  useEffect(() => {
    const t = threads[focusedIndex];
    if (t) setSelectedThreadId(t.id);
  }, [focusedIndex, threads]);

  const toggleSelect = (id: string, checked: boolean) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (checked) next.add(id);
      else next.delete(id);
      return next;
    });
  };

  const bulkArchive = async () => {
    try {
      await bulkMutation.mutateAsync({
        threadIds: [...selectedIds],
        status: "CLOSED",
      });
      toast.success(`Archived ${selectedIds.size} threads`);
      setSelectedIds(new Set());
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  const bulkAssign = async (assigneeUserId: string) => {
    try {
      await Promise.all(
        [...selectedIds].map((threadId) =>
          assignMutation.mutateAsync({ threadId, userId: assigneeUserId }),
        ),
      );
      toast.success(`Assigned ${selectedIds.size} threads`);
      setSelectedIds(new Set());
      setAssignOpen(false);
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  const sendReply = async (payload: {
    bodyHtml: string;
    subject: string;
    to: string[];
    cc?: string[];
    replyMode: "REPLY" | "REPLY_ALL" | "FORWARD";
    attachmentIds: string[];
  }) => {
    if (!selectedThreadId) return;
    await replyMutation.mutateAsync({
      threadId: selectedThreadId,
      ...payload,
    });
    toast.success(payload.replyMode === "FORWARD" ? "Message forwarded" : "Reply sent");
  };

  const uploadAttachment = async (file: File) => {
    const uploaded = await uploadMutation.mutateAsync({ file, purpose: "MAIL_ATTACHMENT" });
    return { id: uploaded.id, filename: uploaded.filename };
  };

  if (mailboxesQuery.isError) {
    return <ErrorState message="Failed to load mailboxes" onRetry={() => void mailboxesQuery.refetch()} />;
  }

  const showList = !mobileShowDetail;

  return (
    <div className="flex h-[calc(100dvh-3.5rem)] overflow-hidden">
      <div className="hidden w-48 shrink-0 flex-col border-r border-border bg-surface-muted/30 md:flex">
        <FilterPanel
          mailboxId={mailboxId}
          setMailboxId={setMailboxId}
          viewId={viewId}
          setViewId={setViewId}
          savedViews={savedViews}
          mailboxes={mailboxes}
          loading={mailboxesQuery.isLoading}
        />
      </div>

      <Dialog open={filterOpen} onOpenChange={setFilterOpen}>
        <DialogContent className="max-h-[85dvh] overflow-y-auto p-0 md:hidden">
          <DialogHeader className="p-4 pb-0">
            <DialogTitle>Filters</DialogTitle>
          </DialogHeader>
          <FilterPanel
            mailboxId={mailboxId}
            setMailboxId={(id) => {
              setMailboxId(id);
              setFilterOpen(false);
            }}
            viewId={viewId}
            setViewId={(id) => {
              setViewId(id);
              setFilterOpen(false);
            }}
            savedViews={savedViews}
            mailboxes={mailboxes}
            loading={mailboxesQuery.isLoading}
          />
        </DialogContent>
      </Dialog>

      <div
        className={cn(
          "flex w-full shrink-0 flex-col border-r border-border md:w-80 lg:w-96",
          !showList && "hidden lg:flex",
        )}
      >
        <div className="flex items-center gap-2 border-b border-border p-2">
          <Button
            variant="ghost"
            size="icon"
            className="md:hidden"
            onClick={() => setFilterOpen(true)}
            aria-label="Open filters"
          >
            <Filter className="h-4 w-4" />
          </Button>
          <div className="relative flex-1">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-text-muted" aria-hidden="true" />
            <Input
              id="inbox-search"
              placeholder="Search threads…"
              className="pl-8"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              aria-label="Search threads"
            />
          </div>
          <Button variant="ghost" size="icon" onClick={() => setShowShortcuts(true)} aria-label="Keyboard shortcuts">
            <HelpCircle className="h-4 w-4" />
          </Button>
        </div>
        {selectedIds.size > 0 && (
          <div className="flex flex-wrap items-center gap-2 border-b border-border bg-surface-muted px-3 py-2 text-sm">
            <span>{selectedIds.size} selected</span>
            <PermissionGate permission={PERMISSIONS.MAIL_THREAD_UPDATE}>
              <Button size="sm" variant="outline" disabled={bulkMutation.isPending} onClick={() => void bulkArchive()}>
                <Archive className="h-3 w-3" /> Archive
              </Button>
            </PermissionGate>
            <PermissionGate permission={PERMISSIONS.MAIL_ASSIGN}>
              <Button size="sm" variant="outline" onClick={() => setAssignOpen(true)}>
                <UserPlus className="h-3 w-3" /> Assign
              </Button>
            </PermissionGate>
          </div>
        )}
        <CursorList
          items={threads}
          hasMore={!!threadsQuery.hasNextPage}
          isLoading={threadsQuery.isLoading}
          isError={threadsQuery.isError}
          isFetchingNextPage={threadsQuery.isFetchingNextPage}
          onLoadMore={() => void threadsQuery.fetchNextPage()}
          getKey={(t) => t.id}
          emptyTitle="Inbox zero"
          emptyDescription="No threads match your current filters."
          renderItem={(item) => (
            <ThreadListRow
              thread={item}
              active={item.id === selectedThreadId}
              checked={selectedIds.has(item.id)}
              onSelect={() => selectThread(item.id)}
              onCheck={(c) => toggleSelect(item.id, c)}
            />
          )}
          className="flex-1"
        />
      </div>

      <div
        className={cn(
          "min-w-0 flex-1 flex-col",
          mobileShowDetail ? "flex" : "hidden lg:flex",
        )}
      >
        <div className="flex items-center gap-2 border-b border-border px-2 py-1 lg:hidden">
          <Button variant="ghost" size="sm" onClick={() => setMobileShowDetail(false)}>
            <ArrowLeft className="h-4 w-4" /> Back
          </Button>
        </div>
        {!selectedThreadId || threadQuery.isLoading ? (
          <div className="flex flex-1 items-center justify-center">
            {threadQuery.isLoading ? (
              <Skeleton className="h-64 w-3/4 max-w-lg" />
            ) : (
              <p className="text-sm text-text-muted">Select a thread to read</p>
            )}
          </div>
        ) : threadQuery.isError || !thread ? (
          <ErrorState message="Failed to load thread" onRetry={() => void threadQuery.refetch()} />
        ) : (
          <>
            <div className="border-b border-border px-4 py-3">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                <h2 className="break-words font-semibold">{thread.subject}</h2>
                <div className="flex flex-wrap items-center gap-2">
                  <SlaBadge dueAt={thread.slaDueAt} breached={isSlaBreached(thread)} />
                  <Select
                    value={thread.status}
                    onValueChange={(v) =>
                      void updateMutation.mutateAsync({ threadId: selectedThreadId, data: { status: v } })
                    }
                  >
                    <SelectTrigger className="h-8 w-36">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {["OPEN", "PENDING_CUSTOMER", "ON_HOLD", "RESOLVED", "CLOSED"].map((s) => (
                        <SelectItem key={s} value={s}>{s.replace(/_/g, " ")}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>
            </div>

            <MailThreadAiPanel threadId={selectedThreadId} />

            <Tabs defaultValue="messages" className="flex min-h-0 flex-1 flex-col">
              <TabsList className="mx-4 mt-2 w-full max-w-full justify-start overflow-x-auto">
                <TabsTrigger value="messages">Messages</TabsTrigger>
                <TabsTrigger value="notes">Notes ({notes.length})</TabsTrigger>
                <TabsTrigger value="activity">Activity</TabsTrigger>
              </TabsList>

              <TabsContent value="messages" className="flex min-h-0 flex-1 flex-col overflow-hidden">
                <div className="flex-1 space-y-4 overflow-auto p-4">
                  {messages.map((msg) => (
                    <div
                      key={msg.id}
                      className={cn(
                        "rounded-lg border border-border p-4",
                        msg.direction === "OUTBOUND" && "ml-0 bg-surface-muted/50 sm:ml-8",
                      )}
                    >
                      <div className="mb-2 flex items-center gap-2 text-sm">
                        <Avatar className="h-6 w-6">
                          <AvatarFallback className="text-[10px]">
                            {(msg.fromName ?? msg.fromAddress).slice(0, 2).toUpperCase()}
                          </AvatarFallback>
                        </Avatar>
                        <span className="font-medium">{msg.fromName ?? msg.fromAddress}</span>
                        <RelativeTime date={msg.occurredAt} className="text-xs" />
                      </div>
                      {msg.bodyHtml ? (
                        <SanitizedEmailHtml html={msg.bodyHtml} />
                      ) : msg.bodyText ? (
                        <CollapsibleQuotedText text={msg.bodyText} />
                      ) : null}
                      {msg.attachmentCount > 0 && (
                        <p className="mt-2 text-xs text-text-muted">{msg.attachmentCount} attachment(s)</p>
                      )}
                    </div>
                  ))}
                </div>

                <PermissionGate permission={PERMISSIONS.MAIL_SEND}>
                  <ReplyComposer
                    threadId={selectedThreadId}
                    threadSubject={thread.subject}
                    customerEmail={thread.customerEmail}
                    cannedReplies={cannedQuery.data ?? []}
                    onSend={sendReply}
                    onUpload={uploadAttachment}
                    isSending={replyMutation.isPending}
                    isUploading={uploadMutation.isPending}
                  />
                </PermissionGate>
              </TabsContent>

              <TabsContent value="notes" className="overflow-auto p-4">
                {notes.length === 0 ? (
                  <p className="text-sm text-text-muted">No internal notes yet.</p>
                ) : (
                  notes.map((note) => (
                    <div key={note.id} className="mb-3 rounded-lg border border-dashed border-warning/40 bg-warning/5 p-3">
                      <RelativeTime date={note.createdAt} className="text-xs text-text-muted" />
                      <SanitizedEmailHtml html={note.bodyHtml} />
                    </div>
                  ))
                )}
              </TabsContent>

              <TabsContent value="activity" className="overflow-auto p-4">
                <ul className="space-y-2">
                  {events.map((evt, i) => (
                    <li key={`${evt.createdAt}-${i}`} className="flex flex-col gap-1 text-sm sm:flex-row sm:justify-between">
                      <span>{eventDetail(evt)}</span>
                      <RelativeTime date={evt.createdAt} className="text-xs" />
                    </li>
                  ))}
                </ul>
              </TabsContent>
            </Tabs>
          </>
        )}
      </div>

      <Dialog open={showShortcuts} onOpenChange={setShowShortcuts}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Keyboard shortcuts</DialogTitle>
          </DialogHeader>
          <dl className="space-y-2">
            {SHORTCUTS.map(({ keys, action }) => (
              <div key={keys} className="flex justify-between text-sm">
                <dt className="text-text-muted">{action}</dt>
                <dd><kbd className="rounded bg-surface-muted px-2 py-0.5 font-mono text-xs">{keys}</kbd></dd>
              </div>
            ))}
          </dl>
        </DialogContent>
      </Dialog>

      <Dialog open={assignOpen} onOpenChange={setAssignOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Assign threads</DialogTitle>
          </DialogHeader>
          <div className="max-h-60 space-y-1 overflow-y-auto">
            {(membersQuery.data?.pages.flatMap((p) => p.items) ?? []).map((m) => (
              <Button
                key={m.id}
                variant="ghost"
                className="w-full justify-start"
                onClick={() => void bulkAssign(m.userId)}
              >
                {m.displayName} ({m.email})
              </Button>
            ))}
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
