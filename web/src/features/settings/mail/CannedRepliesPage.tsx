import { useState } from "react";
import { AlertTriangle, MessageSquare, Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { EmptyState } from "@/components/shared/states";
import { Skeleton } from "@/components/ui/skeleton";
import { getApiErrorMessage } from "@/lib/api-client";
import {
  useCannedReplies,
  useCreateCannedReply,
  useDeleteCannedReply,
  useUpdateCannedReply,
  type CannedReply,
} from "@/features/helpdesk/api";
import { useAdminMailboxes } from "@/features/helpdesk/mailbox-admin";
import { adminErrorHint } from "./MailSettingsLayout";
import { ConfirmDialog } from "./ConfirmDialog";

export default function CannedRepliesPage() {
  const replies = useCannedReplies();
  const mailboxes = useAdminMailboxes();
  const create = useCreateCannedReply();
  const update = useUpdateCannedReply();
  const remove = useDeleteCannedReply();

  const [showCreate, setShowCreate] = useState(false);
  const [title, setTitle] = useState("");
  const [bodyHtml, setBodyHtml] = useState("");
  const [shortcut, setShortcut] = useState("");
  const [subject, setSubject] = useState("");
  const [mailboxId, setMailboxId] = useState("");
  const [editing, setEditing] = useState<CannedReply | null>(null);
  const [error, setError] = useState<string>();
  const [confirmDelete, setConfirmDelete] = useState<CannedReply>();

  const resetForm = () => {
    setTitle("");
    setBodyHtml("");
    setShortcut("");
    setSubject("");
    setMailboxId("");
  };

  const submitCreate = async () => {
    setError(undefined);
    try {
      await create.mutateAsync({
        title: title.trim(),
        bodyHtml: bodyHtml.trim(),
        shortcut: shortcut.trim() || undefined,
        subject: subject.trim() || undefined,
        mailboxId: mailboxId || undefined,
      });
      setShowCreate(false);
      resetForm();
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  const submitEdit = async () => {
    if (!editing) return;
    setError(undefined);
    try {
      await update.mutateAsync({
        replyId: editing.id,
        title: title.trim(),
        bodyHtml: bodyHtml.trim(),
        shortcut: shortcut.trim() || undefined,
        subject: subject.trim() || undefined,
        mailboxId: mailboxId || undefined,
      });
      setEditing(null);
      resetForm();
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  const startEdit = (reply: CannedReply) => {
    setEditing(reply);
    setTitle(reply.title);
    setBodyHtml(reply.bodyHtml);
    setShortcut(reply.shortcut ?? "");
    setSubject(reply.subject ?? "");
    setMailboxId(reply.mailboxId ?? "");
    setShowCreate(false);
  };

  const doDelete = async () => {
    if (!confirmDelete) return;
    setError(undefined);
    try {
      await remove.mutateAsync(confirmDelete.id);
      setConfirmDelete(undefined);
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  const formOpen = showCreate || editing;

  return (
    <div className="mx-auto max-w-3xl p-6">
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="font-display text-xl font-semibold tracking-tight">Canned replies</h1>
          <p className="mt-1 text-sm text-text-muted">
            Saved responses agents can insert when replying to tickets.
          </p>
        </div>
        <Button
          size="sm"
          onClick={() => {
            setEditing(null);
            resetForm();
            setShowCreate(true);
          }}
        >
          <Plus className="size-4" />
          Add reply
        </Button>
      </div>

      {error ? (
        <p className="mb-4 rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </p>
      ) : null}

      {formOpen ? (
        <div className="mb-6 space-y-3 rounded-lg border border-border p-4">
          <h2 className="text-sm font-medium">{editing ? "Edit reply" : "New reply"}</h2>
          <Input
            placeholder="Title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            aria-label="Title"
          />
          <Input
            placeholder="Shortcut (optional)"
            value={shortcut}
            onChange={(e) => setShortcut(e.target.value)}
            aria-label="Shortcut"
          />
          <Input
            placeholder="Subject override (optional)"
            value={subject}
            onChange={(e) => setSubject(e.target.value)}
            aria-label="Subject"
          />
          <select
            aria-label="Limit to mailbox"
            value={mailboxId}
            onChange={(e) => setMailboxId(e.target.value)}
            className="w-full rounded-md border border-border bg-surface px-3 py-2 text-sm"
          >
            <option value="">All mailboxes</option>
            {(mailboxes.data ?? []).map((m) => (
              <option key={m.id} value={m.id}>
                {m.name}
              </option>
            ))}
          </select>
          <Textarea
            placeholder="Body (HTML)"
            value={bodyHtml}
            onChange={(e) => setBodyHtml(e.target.value)}
            rows={6}
            aria-label="Body"
          />
          <div className="flex gap-2">
            <Button
              size="sm"
              disabled={(create.isPending || update.isPending) || !title.trim() || !bodyHtml.trim()}
              onClick={() => void (editing ? submitEdit() : submitCreate())}
            >
              {editing ? "Save" : "Create"}
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={() => {
                setShowCreate(false);
                setEditing(null);
                resetForm();
              }}
            >
              Cancel
            </Button>
          </div>
        </div>
      ) : null}

      {replies.isPending ? (
        <div className="space-y-2">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-16 w-full" />
          ))}
        </div>
      ) : replies.isError ? (
        <EmptyState
          icon={<AlertTriangle className="size-8" />}
          title="Canned replies could not be loaded"
          description={adminErrorHint(replies.error) ?? getApiErrorMessage(replies.error)}
        />
      ) : (replies.data ?? []).length === 0 ? (
        <EmptyState
          icon={<MessageSquare className="size-8" />}
          title="No canned replies yet"
          description="Add saved responses your team uses often."
        />
      ) : (
        <ul className="divide-y divide-border rounded-lg border border-border">
          {(replies.data ?? []).map((reply) => (
            <li key={reply.id} className="flex items-start gap-3 px-4 py-3">
              <div className="min-w-0 flex-1">
                <div className="text-sm font-medium">{reply.title}</div>
                {reply.shortcut ? (
                  <div className="font-mono text-xs text-text-muted">/{reply.shortcut}</div>
                ) : null}
                <div className="mt-1 line-clamp-2 text-xs text-text-muted">{stripHtml(reply.bodyHtml)}</div>
                <div className="mt-1 text-[10px] text-text-muted">
                  Used {reply.usageCount} time{reply.usageCount === 1 ? "" : "s"}
                </div>
              </div>
              <Button variant="ghost" size="sm" onClick={() => startEdit(reply)}>
                Edit
              </Button>
              <Button
                variant="ghost"
                size="icon"
                aria-label={`Delete ${reply.title}`}
                onClick={() => setConfirmDelete(reply)}
              >
                <Trash2 className="size-4 text-destructive" />
              </Button>
            </li>
          ))}
        </ul>
      )}

      {confirmDelete ? (
        <ConfirmDialog
          title={`Delete ${confirmDelete.title}?`}
          hint="Agents will no longer be able to insert this reply."
          busy={remove.isPending}
          onCancel={() => setConfirmDelete(undefined)}
          onConfirm={() => void doDelete()}
        />
      ) : null}
    </div>
  );
}

function stripHtml(html: string): string {
  return html.replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim();
}
