import { Loader2, Paperclip, Sparkles, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
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
import { Textarea } from "@/components/ui/textarea";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { getApiErrorMessage } from "@/lib/api-client";
import type { ReplyMode } from "@/lib/schemas/mail";
import { PERMISSIONS } from "@/lib/permissions";
import { useAuth } from "@/lib/auth";
import { useAiStatus, useMailAdaptCannedReply } from "@/features/ai/api";
import { StreamSuggestControls } from "@/features/ai/components/StreamSuggestControls";
import { useStreamSuggest } from "@/features/ai/hooks/useStreamSuggest";

export type PendingAttachment = {
  id: string;
  filename: string;
};

export type ReplyPayload = {
  bodyHtml: string;
  subject: string;
  to: string[];
  cc?: string[];
  replyMode: ReplyMode;
  attachmentIds: string[];
};

interface ReplyComposerProps {
  threadId: string;
  threadSubject: string;
  customerEmail: string | null | undefined;
  cannedReplies: { id: string; title: string; bodyHtml: string }[];
  onSend: (payload: ReplyPayload) => Promise<void>;
  onUpload: (file: File) => Promise<PendingAttachment>;
  isSending: boolean;
  isUploading: boolean;
}

function parseRecipients(value: string): string[] {
  return value
    .split(/[,;\s]+/)
    .map((s) => s.trim())
    .filter(Boolean);
}

function buildSubject(mode: ReplyMode, threadSubject: string): string {
  const subject = threadSubject.trim();
  if (mode === "FORWARD") {
    return subject.toLowerCase().startsWith("fwd:") ? subject : `Fwd: ${subject}`;
  }
  return subject.toLowerCase().startsWith("re:") ? subject : `Re: ${subject}`;
}

export function ReplyComposer({
  threadId,
  threadSubject,
  customerEmail,
  cannedReplies,
  onSend,
  onUpload,
  isSending,
  isUploading,
}: ReplyComposerProps) {
  const { permissions } = useAuth();
  const statusQuery = useAiStatus();
  const adaptCanned = useMailAdaptCannedReply();
  const streamSuggest = useStreamSuggest({
    onError: (message) => toast.error(message),
  });
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [body, setBody] = useState("");
  const [replyMode, setReplyMode] = useState<ReplyMode>("REPLY");
  const [forwardTo, setForwardTo] = useState("");
  const [forwardCc, setForwardCc] = useState("");
  const [pendingAttachments, setPendingAttachments] = useState<PendingAttachment[]>([]);
  const [adaptingId, setAdaptingId] = useState<string | null>(null);

  const hasAiUse = permissions.includes(PERMISSIONS.AI_USE);

  useEffect(() => {
    if (streamSuggest.text) setBody(streamSuggest.text);
  }, [streamSuggest.text]);

  useEffect(() => {
    streamSuggest.cancel();
  }, [threadId, streamSuggest.cancel]);

  const removeAttachment = (id: string) => {
    setPendingAttachments((prev) => prev.filter((a) => a.id !== id));
  };

  const onAttach = async (file: File) => {
    try {
      const uploaded = await onUpload(file);
      setPendingAttachments((prev) => [...prev, uploaded]);
      toast.success(`Attached ${uploaded.filename}`);
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    } finally {
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  const send = async () => {
    if (!body.trim()) return;

    let to: string[];
    let cc: string[] | undefined;

    if (replyMode === "FORWARD") {
      to = parseRecipients(forwardTo);
      if (to.length === 0) {
        toast.error("Forward requires at least one recipient");
        return;
      }
      const parsedCc = parseRecipients(forwardCc);
      cc = parsedCc.length > 0 ? parsedCc : undefined;
    } else {
      if (!customerEmail) {
        toast.error("No customer email on this thread");
        return;
      }
      to = [customerEmail];
    }

    try {
      await onSend({
        bodyHtml: body,
        subject: buildSubject(replyMode, threadSubject),
        to,
        cc,
        replyMode,
        attachmentIds: pendingAttachments.map((a) => a.id),
      });
      setBody("");
      setForwardTo("");
      setForwardCc("");
      setPendingAttachments([]);
      setReplyMode("REPLY");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  const insertCanned = (bodyHtml: string) => setBody(bodyHtml);

  const adaptCannedReply = async (cannedReplyId: string, title: string) => {
    setAdaptingId(cannedReplyId);
    try {
      const result = await adaptCanned.mutateAsync({ threadId, cannedReplyId });
      if (!result.available) {
        toast.error("AI is not available. Configure a provider in AI settings.");
        return;
      }
      setBody(result.draft);
      toast.success(`Adapted "${title}" for this thread`);
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    } finally {
      setAdaptingId(null);
    }
  };

  return (
    <div className="border-t border-border p-4 pb-[calc(1rem+env(safe-area-inset-bottom))]">
      <div className="mb-2 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <Select value={replyMode} onValueChange={(v) => setReplyMode(v as ReplyMode)}>
          <SelectTrigger className="h-8 w-full sm:w-40">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="REPLY">Reply</SelectItem>
            <SelectItem value="REPLY_ALL">Reply all</SelectItem>
            <SelectItem value="FORWARD">Forward</SelectItem>
          </SelectContent>
        </Select>
        {replyMode !== "FORWARD" && customerEmail && (
          <p className="break-all text-xs text-text-muted">
            To: {customerEmail}
            {replyMode === "REPLY_ALL" && " (+ others on thread)"}
          </p>
        )}
      </div>

      {replyMode === "FORWARD" && (
        <div className="mb-2 space-y-2">
          <Input
            placeholder="To (comma-separated)"
            value={forwardTo}
            onChange={(e) => setForwardTo(e.target.value)}
            aria-label="Forward recipients"
          />
          <Input
            placeholder="Cc (optional, comma-separated)"
            value={forwardCc}
            onChange={(e) => setForwardCc(e.target.value)}
            aria-label="Forward Cc recipients"
          />
        </div>
      )}

      <Textarea
        id="composer"
        placeholder={
          replyMode === "FORWARD" ? "Add a message above the forwarded content…" : "Write your reply…"
        }
        value={body}
        onChange={(e) => setBody(e.target.value)}
        className="min-h-[72px] max-h-[30dvh] sm:min-h-[100px] sm:max-h-none"
      />

      {pendingAttachments.length > 0 && (
        <ul className="mt-2 flex flex-wrap gap-2">
          {pendingAttachments.map((attachment) => (
            <li key={attachment.id}>
              <Badge variant="secondary" className="gap-1 pr-1">
                <Paperclip className="h-3 w-3" aria-hidden="true" />
                <span className="max-w-[12rem] truncate">{attachment.filename}</span>
                <button
                  type="button"
                  className="ml-1 rounded p-0.5 hover:bg-surface-muted"
                  onClick={() => removeAttachment(attachment.id)}
                  aria-label={`Remove ${attachment.filename}`}
                >
                  <X className="h-3 w-3" />
                </button>
              </Badge>
            </li>
          ))}
        </ul>
      )}

      <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-1">
          <input
            ref={fileInputRef}
            type="file"
            className="hidden"
            onChange={(e) => {
              const f = e.target.files?.[0];
              if (f) void onAttach(f);
            }}
          />
          <Button
            variant="ghost"
            size="sm"
            disabled={isUploading}
            onClick={() => fileInputRef.current?.click()}
          >
            <Paperclip className="h-4 w-4" aria-hidden="true" />
            Attach
          </Button>
          <PermissionGate permission={PERMISSIONS.AI_USE}>
            <StreamSuggestControls
              status={statusQuery.data}
              streaming={streamSuggest.streaming}
              hasPermission={hasAiUse}
              onSuggest={() => streamSuggest.startMailSuggest(threadId)}
              onCancel={streamSuggest.cancel}
            />
          </PermissionGate>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button size="sm" variant="ghost">
                Canned replies
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start" className="max-w-[min(100vw-2rem,20rem)]">
              {cannedReplies.map((cr) => (
                <div key={cr.id}>
                  <DropdownMenuItem onSelect={() => insertCanned(cr.bodyHtml)}>
                    {cr.title}
                  </DropdownMenuItem>
                  <PermissionGate permission={PERMISSIONS.AI_USE}>
                    <DropdownMenuItem
                      disabled={!statusQuery.data?.configured || adaptingId === cr.id}
                      onSelect={() => void adaptCannedReply(cr.id, cr.title)}
                      className="gap-2 pl-6 text-xs text-text-muted"
                    >
                      {adaptingId === cr.id ? (
                        <Loader2 className="h-3 w-3 animate-spin" />
                      ) : (
                        <Sparkles className="h-3 w-3" />
                      )}
                      Adapt with AI
                    </DropdownMenuItem>
                  </PermissionGate>
                </div>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
        <Button onClick={() => void send()} disabled={isSending || !body.trim()}>
          {replyMode === "FORWARD" ? "Send forward" : "Send reply"}
        </Button>
      </div>
    </div>
  );
}
