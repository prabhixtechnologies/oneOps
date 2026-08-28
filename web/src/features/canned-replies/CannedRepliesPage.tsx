import { Plus } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { PageHeader } from "@/components/shared/PageHeader";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { ErrorState } from "@/components/shared/states";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import { useCannedReplies, useCreateCannedReply } from "@/features/mail/api";
import { getApiErrorMessage } from "@/lib/api-client";
import { PERMISSIONS } from "@/lib/permissions";

export default function CannedRepliesPage() {
  const repliesQuery = useCannedReplies();
  const createReply = useCreateCannedReply();
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState("");
  const [bodyHtml, setBodyHtml] = useState("");
  const [shortcut, setShortcut] = useState("");

  const onCreate = async () => {
    try {
      await createReply.mutateAsync({ title, bodyHtml, shortcut: shortcut || undefined });
      toast.success("Canned reply created");
      setOpen(false);
      setTitle("");
      setBodyHtml("");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  if (repliesQuery.isError) {
    return <ErrorState message="Failed to load canned replies" onRetry={() => void repliesQuery.refetch()} />;
  }

  return (
    <div className="space-y-6 p-4 md:p-6">
      <PageHeader
        title="Canned replies"
        description="Reusable reply snippets for your inbox. Edit and delete are not available via the API yet."
        actions={
          <PermissionGate permission={PERMISSIONS.MAIL_THREAD_UPDATE}>
            <Button onClick={() => setOpen(true)}>
              <Plus className="h-4 w-4" /> New reply
            </Button>
          </PermissionGate>
        }
      />

      {repliesQuery.isLoading ? (
        <Skeleton className="h-48" />
      ) : (
        <div className="space-y-3">
          {(repliesQuery.data ?? []).map((cr) => (
            <div key={cr.id} className="rounded-lg border border-border p-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="font-medium">{cr.title}</p>
                {cr.shortcut && <code className="text-xs text-text-muted">/{cr.shortcut}</code>}
              </div>
              <p className="mt-2 line-clamp-3 text-sm text-text-muted">{cr.bodyHtml.replace(/<[^>]+>/g, " ")}</p>
            </div>
          ))}
        </div>
      )}

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-h-[90dvh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>New canned reply</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label>Title</Label>
              <Input value={title} onChange={(e) => setTitle(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>Shortcut (optional)</Label>
              <Input value={shortcut} onChange={(e) => setShortcut(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>Body (HTML)</Label>
              <Textarea value={bodyHtml} onChange={(e) => setBodyHtml(e.target.value)} className="min-h-[120px]" />
            </div>
          </div>
          <DialogFooter>
            <Button onClick={() => void onCreate()} disabled={createReply.isPending || !title || !bodyHtml}>
              Create
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
