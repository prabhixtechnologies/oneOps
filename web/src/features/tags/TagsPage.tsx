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
import { Skeleton } from "@/components/ui/skeleton";
import { useCreateMailTag, useMailTags } from "@/features/mail/api";
import { getApiErrorMessage } from "@/lib/api-client";
import { PERMISSIONS } from "@/lib/permissions";

export default function TagsPage() {
  const tagsQuery = useMailTags();
  const createTag = useCreateMailTag();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [colour, setColour] = useState("#6366f1");

  const onCreate = async () => {
    try {
      await createTag.mutateAsync({ name, colour });
      toast.success("Tag created");
      setOpen(false);
      setName("");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  if (tagsQuery.isError) {
    return <ErrorState message="Failed to load tags" onRetry={() => void tagsQuery.refetch()} />;
  }

  return (
    <div className="space-y-6 p-4 md:p-6">
      <PageHeader
        title="Mail tags"
        description="Organize threads with coloured tags. Update and delete are not available via the API yet."
        actions={
          <PermissionGate permission={PERMISSIONS.MAIL_THREAD_UPDATE}>
            <Button onClick={() => setOpen(true)}>
              <Plus className="h-4 w-4" /> New tag
            </Button>
          </PermissionGate>
        }
      />

      {tagsQuery.isLoading ? (
        <Skeleton className="h-48" />
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {(tagsQuery.data ?? []).map((tag) => (
            <div key={tag.id} className="rounded-lg border border-border p-4">
              <div className="flex items-center gap-2">
                <span className="h-3 w-3 rounded-full" style={{ backgroundColor: tag.colour }} />
                <span className="font-medium">{tag.name}</span>
              </div>
              <p className="mt-1 text-xs text-text-muted">{tag.slug} · used {tag.usageCount}×</p>
            </div>
          ))}
        </div>
      )}

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create tag</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label>Name</Label>
              <Input value={name} onChange={(e) => setName(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>Colour</Label>
              <Input type="color" value={colour} onChange={(e) => setColour(e.target.value)} />
            </div>
          </div>
          <DialogFooter>
            <Button onClick={() => void onCreate()} disabled={createTag.isPending || !name.trim()}>
              Create
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
