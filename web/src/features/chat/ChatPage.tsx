import {
  ArrowLeft,
  Filter,
  HelpCircle,
  Lock,
  MessageSquare,
  Search,
  Settings,
  UserPlus,
  Wifi,
  WifiOff,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Separator } from "@/components/ui/separator";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { CursorList } from "@/components/shared/CursorList";
import { RelativeTime } from "@/components/shared/RelativeTime";
import { ErrorState, EmptyState } from "@/components/shared/states";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { connectChatStream, type StreamConnectionState } from "@/lib/sse";
import type { ConversationSummary } from "@/lib/schemas/chat";
import { isConversationUnread } from "@/lib/schemas/chat";
import { MessageBubble } from "@/features/chat/MessageBubble";
import {
  useAssignChatConversation,
  useChatCannedReplies,
  useChatConversation,
  useChatConversations,
  useChatCounts,
  useSendChatMessage,
  useUpdateChatConversation,
} from "@/features/chat/api";
import { useVisitor, useVisitorPageViews } from "@/features/visitors/api";
import { useMembers } from "@/features/org/api";
import { useAuth } from "@/lib/auth";
import { PERMISSIONS } from "@/lib/permissions";
import { cn } from "@/lib/utils";
import { Skeleton } from "@/components/ui/skeleton";
import { getApiErrorMessage } from "@/lib/api-client";
import { useAiStatus, useChatRewrite } from "@/features/ai/api";
import { ChatConversationAiPanel } from "@/features/ai/components/ChatConversationAiPanel";
import { RewriteDraftMenu, StreamSuggestControls } from "@/features/ai/components/StreamSuggestControls";
import { useStreamSuggest } from "@/features/ai/hooks/useStreamSuggest";

type Queue = "mine" | "unassigned" | "all";
type ComposerMode = "reply" | "note";

const SHORTCUTS = [
  { keys: "j / k", action: "Navigate conversations" },
  { keys: "e", action: "Resolve conversation" },
  { keys: "a", action: "Assign to me" },
  { keys: "r", action: "Reply to visitor" },
  { keys: "n", action: "Write internal note" },
  { keys: "/", action: "Focus search" },
  { keys: "?", action: "Show shortcuts" },
];

const STATUSES = ["OPEN", "PENDING", "RESOLVED", "CLOSED"] as const;
const PRIORITIES = ["LOW", "NORMAL", "HIGH", "URGENT"] as const;

function ConnectionBadge({ state }: { state: StreamConnectionState }) {
  const labels: Record<StreamConnectionState, string> = {
    connecting: "Connecting…",
    connected: "Live",
    disconnected: "Offline",
    error: "Reconnecting…",
  };
  const icons = {
    connecting: Wifi,
    connected: Wifi,
    disconnected: WifiOff,
    error: WifiOff,
  };
  const Icon = icons[state];
  return (
    <Badge
      variant={state === "connected" ? "success" : "secondary"}
      className="gap-1 text-[10px]"
      aria-live="polite"
    >
      <Icon className="h-3 w-3" aria-hidden="true" />
      {labels[state]}
    </Badge>
  );
}

function ConversationRow({
  conversation,
  active,
  onSelect,
}: {
  conversation: ConversationSummary;
  active: boolean;
  onSelect: () => void;
}) {
  const unread = isConversationUnread(conversation);
  const label = conversation.visitorName || conversation.visitorEmail || "Visitor";
  return (
    <div
      className={cn(
        "cursor-pointer border-b border-border px-3 py-2.5 text-sm hover:bg-surface-muted/50",
        active && "border-l-2 border-l-primary bg-primary/5",
      )}
      onClick={onSelect}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => e.key === "Enter" && onSelect()}
    >
      <div className="flex items-center gap-2">
        {unread && <span className="h-2 w-2 shrink-0 rounded-full bg-primary" aria-label="Unread" />}
        <span className={cn("truncate font-medium", unread && "text-text")}>{label}</span>
        {conversation.priority === "URGENT" && (
          <Badge variant="destructive" className="text-[10px]">Urgent</Badge>
        )}
      </div>
      <p className="truncate text-xs text-text-muted">{conversation.subject}</p>
      <p className="truncate text-xs text-text-muted">{conversation.lastMessagePreview}</p>
      <div className="mt-1 flex items-center gap-2">
        <Badge variant="secondary" className="text-[10px]">{conversation.status}</Badge>
        {conversation.lastMessageAt && (
          <RelativeTime date={conversation.lastMessageAt} className="text-[10px]" />
        )}
      </div>
    </div>
  );
}

function VisitorContextPanel({ visitorId }: { visitorId: string | undefined }) {
  const visitorQuery = useVisitor(visitorId);
  const pageViewsQuery = useVisitorPageViews(visitorId);
  const pageViews = pageViewsQuery.data?.pages.flatMap((p) => p.items) ?? [];

  if (!visitorId) {
    return (
      <div className="p-4 text-sm text-text-muted">
        No visitor linked to this conversation.
      </div>
    );
  }

  if (visitorQuery.isLoading) {
    return <div className="p-4"><Skeleton className="h-32" /></div>;
  }

  if (visitorQuery.isError || !visitorQuery.data) {
    return <div className="p-4 text-sm text-destructive">Failed to load visitor context</div>;
  }

  const { visitor, sessions } = visitorQuery.data;
  const latestSession = sessions[0];

  return (
    <div className="space-y-4 p-4 text-sm">
      <div>
        <h3 className="font-semibold">Visitor</h3>
        <p className="mt-1">{visitor.displayName || visitor.email || "Anonymous"}</p>
        {visitor.email && <p className="text-text-muted">{visitor.email}</p>}
        <div className="mt-2 flex flex-wrap gap-1">
          <Badge variant={visitor.identified ? "success" : "secondary"}>
            {visitor.identified ? "Identified" : "Anonymous"}
          </Badge>
          {visitor.consentStatus && (
            <Badge variant="outline">{visitor.consentStatus}</Badge>
          )}
        </div>
      </div>

      {latestSession && (
        <>
          <Separator />
          <div>
            <h3 className="font-semibold">Device & location</h3>
            {/* Each value is a <dd>. They were bare text nodes, so the terms had nothing bound to
                them and a screen reader read the labels without the values they describe. */}
            <dl className="mt-2 space-y-1 text-xs text-text-muted">
              {latestSession.deviceType && <div><dt className="inline font-medium text-text">Device: </dt><dd className="inline">{latestSession.deviceType}</dd></div>}
              {latestSession.browser && <div><dt className="inline font-medium text-text">Browser: </dt><dd className="inline">{latestSession.browser}</dd></div>}
              {latestSession.os && <div><dt className="inline font-medium text-text">OS: </dt><dd className="inline">{latestSession.os}</dd></div>}
              {(latestSession.geoCity || latestSession.geoCountry) && (
                <div>
                  <dt className="inline font-medium text-text">Location: </dt>
                  <dd className="inline">
                    {[latestSession.geoCity, latestSession.geoCountry].filter(Boolean).join(", ")}
                  </dd>
                </div>
              )}
              {latestSession.referrer && (
                <div className="break-all">
                  <dt className="inline font-medium text-text">Referrer: </dt><dd className="inline">{latestSession.referrer}</dd>
                </div>
              )}
            </dl>
          </div>
        </>
      )}

      <Separator />
      <div>
        <h3 className="font-semibold">Page views</h3>
        {pageViewsQuery.isLoading ? (
          <Skeleton className="mt-2 h-16" />
        ) : pageViews.length === 0 ? (
          <p className="mt-2 text-xs text-text-muted">No page views recorded yet.</p>
        ) : (
          <ul className="mt-2 max-h-48 space-y-2 overflow-auto">
            {pageViews.map((pv) => (
              <li key={pv.id} className="rounded border border-border p-2 text-xs">
                <p className="font-medium">{pv.title || pv.path}</p>
                <p className="truncate text-text-muted">{pv.url}</p>
                <RelativeTime date={pv.viewedAt} className="text-[10px]" />
              </li>
            ))}
          </ul>
        )}
      </div>

      <Button variant="outline" size="sm" className="w-full" asChild>
        <Link to={`/visitors/${visitor.id}`}>Full visitor profile</Link>
      </Button>
    </div>
  );
}

export default function ChatPage() {
  const { userId, permissions } = useAuth();
  const [queue, setQueue] = useState<Queue>("all");
  const [statusFilter, setStatusFilter] = useState<string>("");
  const [priorityFilter, setPriorityFilter] = useState<string>("");
  const [search, setSearch] = useState("");
  const [selectedId, setSelectedId] = useState<string | undefined>();
  const [composerBody, setComposerBody] = useState("");
  const [composerMode, setComposerMode] = useState<ComposerMode>("reply");
  const [showShortcuts, setShowShortcuts] = useState(false);
  const [filterOpen, setFilterOpen] = useState(false);
  const [assignOpen, setAssignOpen] = useState(false);
  const [tagInput, setTagInput] = useState("");
  const [focusedIndex, setFocusedIndex] = useState(0);
  const [mobilePane, setMobilePane] = useState<"list" | "thread" | "context">("list");
  const [streamState, setStreamState] = useState<StreamConnectionState>("connecting");
  const liveRegionRef = useRef<HTMLDivElement>(null);

  const filters = useMemo(
    () => ({ queue, status: statusFilter || undefined }),
    [queue, statusFilter],
  );

  const conversationsQuery = useChatConversations(filters);
  const countsQuery = useChatCounts();
  const conversationQuery = useChatConversation(selectedId);
  const cannedQuery = useChatCannedReplies();
  const membersQuery = useMembers("");
  const sendMessage = useSendChatMessage();
  const assignConversation = useAssignChatConversation();
  const updateConversation = useUpdateChatConversation();
  const statusQuery = useAiStatus();
  const rewriteDraft = useChatRewrite();
  const streamSuggest = useStreamSuggest({
    onError: (message) => toast.error(message),
  });

  const selectedIdRef = useRef(selectedId);
  selectedIdRef.current = selectedId;
  const conversationsQueryRef = useRef(conversationsQuery);
  conversationsQueryRef.current = conversationsQuery;
  const countsQueryRef = useRef(countsQuery);
  countsQueryRef.current = countsQuery;
  const conversationQueryRef = useRef(conversationQuery);
  conversationQueryRef.current = conversationQuery;

  const hasAiUse = permissions.includes(PERMISSIONS.AI_USE);

  const rawConversations = useMemo(
    () => conversationsQuery.data?.pages.flatMap((p) => p.items) ?? [],
    [conversationsQuery.data],
  );

  const conversations = useMemo(() => {
    let list = rawConversations;
    if (priorityFilter) list = list.filter((c) => c.priority === priorityFilter);
    if (search.trim()) {
      const q = search.toLowerCase();
      list = list.filter(
        (c) =>
          (c.subject?.toLowerCase().includes(q) ?? false) ||
          (c.visitorName?.toLowerCase().includes(q) ?? false) ||
          (c.visitorEmail?.toLowerCase().includes(q) ?? false),
      );
    }
    return list;
  }, [rawConversations, priorityFilter, search]);

  const conversation = conversationQuery.data?.conversation;
  const messages = conversationQuery.data?.messages ?? [];
  const visitorLabel = conversation?.visitorName || conversation?.visitorEmail || "Visitor";

  useEffect(() => {
    if (streamSuggest.text) setComposerBody(streamSuggest.text);
  }, [streamSuggest.text]);

  useEffect(() => {
    streamSuggest.cancel();
    setComposerBody("");
  }, [selectedId, streamSuggest.cancel]);

  useEffect(() => {
    if (conversations.length > 0 && !selectedId) {
      setSelectedId(conversations[0].id);
    }
  }, [conversations, selectedId]);

  useEffect(() => {
    return connectChatStream(
      (event) => {
        if (
          event.event === "conversation.updated" ||
          event.event === "conversation.new" ||
          event.event === "message.new"
        ) {
          void conversationsQueryRef.current.refetch();
          void countsQueryRef.current.refetch();
          if (event.conversationId && event.conversationId === selectedIdRef.current) {
            void conversationQueryRef.current.refetch();
          }
          if (event.event === "message.new" && liveRegionRef.current) {
            liveRegionRef.current.textContent = "New chat message received";
          }
        }
      },
      undefined,
      setStreamState,
    );
  }, []);

  const selectConversation = (id: string) => {
    setSelectedId(id);
    setMobilePane("thread");
    setComposerMode("reply");
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
        document.getElementById("chat-search")?.focus();
        return;
      }
      if (e.key === "j") {
        setFocusedIndex((i) => Math.min(i + 1, conversations.length - 1));
        return;
      }
      if (e.key === "k") {
        setFocusedIndex((i) => Math.max(i - 1, 0));
        return;
      }
      if (e.key === "e" && selectedId) {
        void updateConversation
          .mutateAsync({ conversationId: selectedId, data: { status: "RESOLVED" } })
          .then(() => toast.success("Conversation resolved"))
          .catch((err) => toast.error(getApiErrorMessage(err)));
      }
      if (e.key === "a" && selectedId && userId) {
        void assignConversation
          .mutateAsync({ conversationId: selectedId, agentId: userId })
          .then(() => toast.success("Assigned to you"))
          .catch((err) => toast.error(getApiErrorMessage(err)));
      }
      if (e.key === "r") {
        setComposerMode("reply");
        document.getElementById("chat-composer")?.focus();
      }
      if (e.key === "n") {
        setComposerMode("note");
        document.getElementById("chat-composer")?.focus();
      }
    },
    [conversations.length, selectedId, userId, updateConversation, assignConversation],
  );

  useEffect(() => {
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [handleKeyDown]);

  useEffect(() => {
    const c = conversations[focusedIndex];
    if (c) setSelectedId(c.id);
  }, [focusedIndex, conversations]);

  const sendComposer = async () => {
    if (!selectedId || !composerBody.trim()) return;
    const isNote = composerMode === "note";
    try {
      await sendMessage.mutateAsync({
        conversationId: selectedId,
        body: composerBody.trim(),
        internal: isNote,
      });
      setComposerBody("");
      if (isNote) {
        toast.success("Private note saved");
      } else {
        toast.success("Reply sent to visitor");
      }
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  const addTag = async () => {
    if (!selectedId || !conversation || !tagInput.trim()) return;
    const tags = [...new Set([...(conversation.tags ?? []), tagInput.trim()])];
    try {
      await updateConversation.mutateAsync({ conversationId: selectedId, data: { tags } });
      setTagInput("");
      toast.success("Tag added");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  const removeTag = async (tag: string) => {
    if (!selectedId || !conversation) return;
    const tags = (conversation.tags ?? []).filter((t) => t !== tag);
    try {
      await updateConversation.mutateAsync({ conversationId: selectedId, data: { tags } });
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  const insertCanned = (body: string) => {
    setComposerBody((prev) => (prev ? `${prev}\n${body}` : body));
    if (composerMode === "note") setComposerMode("reply");
    document.getElementById("chat-composer")?.focus();
  };

  const handleRewrite = async (action: string) => {
    if (!selectedId || !composerBody.trim()) {
      toast.error("Write a draft first");
      return;
    }
    try {
      const result = await rewriteDraft.mutateAsync({
        conversationId: selectedId,
        draft: composerBody,
        action,
      });
      if (!result.available) {
        toast.error("AI is not available. Configure a provider in AI settings.");
        return;
      }
      setComposerBody(result.text);
      toast.success("Draft updated");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  const showList = mobilePane === "list";

  return (
    <div className="flex h-[calc(100dvh-3.5rem)] overflow-hidden pb-[env(safe-area-inset-bottom)]">
      <div ref={liveRegionRef} className="sr-only" aria-live="polite" aria-atomic="true" />

      <div
        className={cn(
          "flex w-full shrink-0 flex-col border-r border-border md:w-80 lg:w-96",
          !showList && "hidden md:flex",
        )}
      >
        <div className="flex flex-wrap items-center gap-2 border-b border-border p-2">
          <Button
            variant="ghost"
            size="icon"
            className="shrink-0 md:hidden"
            onClick={() => setFilterOpen(true)}
            aria-label="Open filters"
          >
            <Filter className="h-4 w-4" />
          </Button>
          <div className="relative min-w-0 flex-1 basis-[8rem]">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-text-muted" aria-hidden="true" />
            <Input
              id="chat-search"
              placeholder="Search conversations…"
              className="pl-8"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              aria-label="Search conversations"
            />
          </div>
          <ConnectionBadge state={streamState} />
          <div className="flex shrink-0 items-center gap-1">
          <Button variant="ghost" size="icon" onClick={() => setShowShortcuts(true)} aria-label="Keyboard shortcuts">
            <HelpCircle className="h-4 w-4" />
          </Button>
          <PermissionGate permission={PERMISSIONS.CHAT_MANAGE}>
            <Button variant="ghost" size="icon" asChild aria-label="Chat settings">
              <Link to="/chat/settings"><Settings className="h-4 w-4" /></Link>
            </Button>
          </PermissionGate>
          </div>
        </div>

        <Tabs value={queue} onValueChange={(v) => setQueue(v as Queue)} className="border-b border-border px-2">
          <TabsList className="h-9 w-full">
            <TabsTrigger value="mine" className="flex-1 text-xs">
              Mine
              {(countsQuery.data?.mineUnread ?? 0) > 0 && (
                <Badge variant="secondary" className="ml-1 text-[10px]">{countsQuery.data?.mineUnread}</Badge>
              )}
            </TabsTrigger>
            <TabsTrigger value="unassigned" className="flex-1 text-xs">
              Unassigned
              {(countsQuery.data?.unassigned ?? 0) > 0 && (
                <Badge variant="secondary" className="ml-1 text-[10px]">{countsQuery.data?.unassigned}</Badge>
              )}
            </TabsTrigger>
            <TabsTrigger value="all" className="flex-1 text-xs">All</TabsTrigger>
          </TabsList>
        </Tabs>

        <div className="flex gap-2 border-b border-border p-2">
          <Select value={statusFilter || "all"} onValueChange={(v) => setStatusFilter(v === "all" ? "" : v)}>
            <SelectTrigger className="h-8 flex-1 text-xs" aria-label="Conversation status filter">
              <SelectValue placeholder="Status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All statuses</SelectItem>
              {STATUSES.map((s) => <SelectItem key={s} value={s}>{s}</SelectItem>)}
            </SelectContent>
          </Select>
          <Select value={priorityFilter || "all"} onValueChange={(v) => setPriorityFilter(v === "all" ? "" : v)}>
            <SelectTrigger className="h-8 flex-1 text-xs" aria-label="Priority filter (loaded conversations)">
              <SelectValue placeholder="Priority" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All priorities</SelectItem>
              {PRIORITIES.map((p) => <SelectItem key={p} value={p}>{p}</SelectItem>)}
            </SelectContent>
          </Select>
        </div>

        <CursorList
          items={conversations}
          hasMore={!!conversationsQuery.hasNextPage}
          isLoading={conversationsQuery.isLoading}
          isError={conversationsQuery.isError}
          isFetchingNextPage={conversationsQuery.isFetchingNextPage}
          onLoadMore={() => void conversationsQuery.fetchNextPage()}
          getKey={(c) => c.id}
          emptyTitle="No conversations"
          emptyDescription="When visitors start chats, they appear here."
          estimateSize={88}
          renderItem={(item) => (
            <ConversationRow
              conversation={item}
              active={item.id === selectedId}
              onSelect={() => selectConversation(item.id)}
            />
          )}
          className="min-h-0 flex-1"
        />
      </div>

      <div
        className={cn(
          "min-w-0 flex-1 flex-col",
          mobilePane === "thread" || mobilePane === "context" ? "flex" : "hidden md:flex",
        )}
      >
        <div className="flex items-center gap-2 border-b border-border px-2 py-1 lg:hidden">
          <Button variant="ghost" size="sm" onClick={() => setMobilePane("list")}>
            <ArrowLeft className="h-4 w-4" /> Back
          </Button>
          <Button
            variant="ghost"
            size="sm"
            className="ml-auto"
            onClick={() => setMobilePane(mobilePane === "context" ? "thread" : "context")}
          >
            {mobilePane === "context" ? "Thread" : "Visitor"}
          </Button>
        </div>

        {!selectedId || conversationQuery.isLoading ? (
          <div className="flex flex-1 items-center justify-center">
            {conversationQuery.isLoading ? (
              <Skeleton className="h-64 w-3/4 max-w-lg" />
            ) : (
              <EmptyState
                className="py-8"
                title="Select a conversation"
                description="Choose a thread from the list to read and reply."
              />
            )}
          </div>
        ) : conversationQuery.isError || !conversation ? (
          <ErrorState message="Failed to load conversation" onRetry={() => void conversationQuery.refetch()} />
        ) : mobilePane === "context" ? (
          <div className="flex-1 overflow-auto lg:hidden">
            <VisitorContextPanel visitorId={conversation.visitorId ?? undefined} />
          </div>
        ) : (
          <div className="flex min-h-0 flex-1 flex-col">
            <div className="border-b border-border px-4 py-3">
              <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div>
                  <h2 className="break-words font-semibold">{conversation.subject}</h2>
                  <p className="break-all text-sm text-text-muted">{visitorLabel}</p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <PermissionGate permission={PERMISSIONS.CHAT_ASSIGN}>
                    <Button size="sm" variant="outline" onClick={() => setAssignOpen(true)}>
                      <UserPlus className="h-3 w-3" /> Assign
                    </Button>
                  </PermissionGate>
                  <Select
                    value={conversation.status}
                    onValueChange={(v) =>
                      void updateConversation
                        .mutateAsync({ conversationId: selectedId, data: { status: v } })
                        .catch((err) => toast.error(getApiErrorMessage(err)))
                    }
                  >
                    <SelectTrigger className="h-8 w-32" aria-label="Conversation status"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {STATUSES.map((s) => <SelectItem key={s} value={s}>{s}</SelectItem>)}
                    </SelectContent>
                  </Select>
                  <Select
                    value={conversation.priority}
                    onValueChange={(v) =>
                      void updateConversation
                        .mutateAsync({ conversationId: selectedId, data: { priority: v } })
                        .catch((err) => toast.error(getApiErrorMessage(err)))
                    }
                  >
                    <SelectTrigger className="h-8 w-32" aria-label="Conversation priority"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {PRIORITIES.map((p) => <SelectItem key={p} value={p}>{p}</SelectItem>)}
                    </SelectContent>
                  </Select>
                </div>
              </div>
              <div className="mt-2 flex flex-wrap items-center gap-1">
                {(conversation.tags ?? []).map((tag) => (
                  <Badge key={tag} variant="outline" className="gap-1">
                    {tag}
                    <button type="button" className="ml-1 text-xs" onClick={() => void removeTag(tag)} aria-label={`Remove tag ${tag}`}>×</button>
                  </Badge>
                ))}
                <div className="flex items-center gap-1">
                  <Input
                    value={tagInput}
                    onChange={(e) => setTagInput(e.target.value)}
                    placeholder="Add tag"
                    className="h-7 w-24 text-xs"
                    aria-label="Add tag"
                    onKeyDown={(e) => e.key === "Enter" && void addTag()}
                  />
                  <Button size="sm" variant="ghost" className="h-7 text-xs" onClick={() => void addTag()}>Add</Button>
                </div>
              </div>
            </div>

            <ChatConversationAiPanel conversationId={selectedId} />

            <div className="flex-1 space-y-4 overflow-auto p-4">
              {messages.map((msg) => (
                <MessageBubble
                  key={msg.id}
                  message={msg}
                  visitorLabel={visitorLabel}
                  agentLabel="Agent"
                />
              ))}
            </div>

            <PermissionGate permission={PERMISSIONS.CHAT_REPLY}>
              <div
                className={cn(
                  "border-t border-border p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] transition-colors",
                  composerMode === "note" && "border-warning bg-warning/5",
                )}
              >
                <div className="mb-3 flex gap-2" role="tablist" aria-label="Composer mode">
                  <Button
                    type="button"
                    role="tab"
                    aria-selected={composerMode === "reply"}
                    variant={composerMode === "reply" ? "default" : "outline"}
                    size="sm"
                    className="flex-1"
                    onClick={() => setComposerMode("reply")}
                  >
                    <MessageSquare className="h-3 w-3" /> Reply to visitor
                  </Button>
                  <Button
                    type="button"
                    role="tab"
                    aria-selected={composerMode === "note"}
                    variant={composerMode === "note" ? "default" : "outline"}
                    size="sm"
                    className={cn(
                      "flex-1",
                      composerMode === "note" && "border-warning bg-warning text-white hover:bg-warning/90",
                    )}
                    onClick={() => setComposerMode("note")}
                  >
                    <Lock className="h-3 w-3" /> Internal note
                  </Button>
                </div>

                {composerMode === "note" && (
                  <div
                    className="mb-3 flex items-center gap-2 rounded-md border-2 border-dashed border-warning bg-warning/10 px-3 py-2 text-sm font-medium text-warning"
                    role="alert"
                  >
                    <Lock className="h-4 w-4 shrink-0" aria-hidden="true" />
                    This note is private — the visitor will never see it.
                  </div>
                )}

                <Textarea
                  id="chat-composer"
                  placeholder={composerMode === "note" ? "Write a private note for your team…" : "Write a reply the visitor will receive…"}
                  value={composerBody}
                  onChange={(e) => setComposerBody(e.target.value)}
                  className={cn(
                    "min-h-[72px] max-h-[30dvh] sm:min-h-[100px] sm:max-h-none",
                    composerMode === "note" && "border-warning focus-visible:ring-warning",
                  )}
                  aria-label={composerMode === "note" ? "Internal note composer" : "Reply composer"}
                />

                <div className="mt-2 flex flex-col gap-2">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex flex-wrap items-center gap-1">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button size="sm" variant="ghost">Canned replies</Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent>
                          {(cannedQuery.data ?? []).map((cr) => (
                            <DropdownMenuItem key={cr.id} onSelect={() => insertCanned(cr.body)}>
                              {cr.title}
                              {cr.shortcut && <span className="ml-2 text-xs text-text-muted">/{cr.shortcut}</span>}
                            </DropdownMenuItem>
                          ))}
                        </DropdownMenuContent>
                      </DropdownMenu>
                      {composerMode === "reply" && selectedId && (
                        <PermissionGate permission={PERMISSIONS.AI_USE}>
                          <StreamSuggestControls
                            status={statusQuery.data}
                            streaming={streamSuggest.streaming}
                            hasPermission={hasAiUse}
                            onSuggest={() => streamSuggest.startChatSuggest(selectedId)}
                            onCancel={streamSuggest.cancel}
                          />
                        </PermissionGate>
                      )}
                    </div>
                    <Button
                      onClick={() => void sendComposer()}
                      disabled={sendMessage.isPending || !composerBody.trim()}
                      variant={composerMode === "note" ? "default" : "default"}
                      className={cn(composerMode === "note" && "bg-warning hover:bg-warning/90")}
                    >
                      {composerMode === "note" ? (
                        <><Lock className="h-4 w-4" /> Save private note</>
                      ) : (
                        "Send reply"
                      )}
                    </Button>
                  </div>
                  {composerMode === "reply" && composerBody.trim() && (
                    <PermissionGate permission={PERMISSIONS.AI_USE}>
                      <RewriteDraftMenu
                        status={statusQuery.data}
                        hasPermission={hasAiUse}
                        disabled={!composerBody.trim()}
                        pending={rewriteDraft.isPending}
                        onRewrite={(action) => void handleRewrite(action)}
                      />
                    </PermissionGate>
                  )}
                </div>
              </div>
            </PermissionGate>
          </div>
        )}
      </div>

      <aside className="hidden w-72 shrink-0 flex-col border-l border-border lg:flex">
        <div className="border-b border-border px-4 py-3">
          <h2 className="text-sm font-semibold">Visitor context</h2>
        </div>
        <div className="flex-1 overflow-auto">
          <VisitorContextPanel visitorId={conversation?.visitorId ?? undefined} />
        </div>
      </aside>

      <Dialog open={filterOpen} onOpenChange={setFilterOpen}>
        <DialogContent className="max-h-[85dvh] overflow-y-auto md:hidden">
          <DialogHeader><DialogTitle>Queue & filters</DialogTitle></DialogHeader>
          <Tabs value={queue} onValueChange={(v) => { setQueue(v as Queue); setFilterOpen(false); }}>
            <TabsList className="w-full">
              <TabsTrigger value="mine" className="flex-1">Mine</TabsTrigger>
              <TabsTrigger value="unassigned" className="flex-1">Unassigned</TabsTrigger>
              <TabsTrigger value="all" className="flex-1">All</TabsTrigger>
            </TabsList>
          </Tabs>
        </DialogContent>
      </Dialog>

      <Dialog open={showShortcuts} onOpenChange={setShowShortcuts}>
        <DialogContent>
          <DialogHeader><DialogTitle>Keyboard shortcuts</DialogTitle></DialogHeader>
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
          <DialogHeader><DialogTitle>Assign conversation</DialogTitle></DialogHeader>
          <div className="max-h-60 space-y-1 overflow-y-auto">
            {(membersQuery.data?.pages.flatMap((p) => p.items) ?? []).map((m) => (
              <Button
                key={m.id}
                variant="ghost"
                className="w-full justify-start"
                disabled={assignConversation.isPending}
                onClick={() => {
                  if (!selectedId) return;
                  void assignConversation
                    .mutateAsync({ conversationId: selectedId, agentId: m.userId })
                    .then(() => {
                      toast.success(`Assigned to ${m.displayName}`);
                      setAssignOpen(false);
                    })
                    .catch((err) => toast.error(getApiErrorMessage(err)));
                }}
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
