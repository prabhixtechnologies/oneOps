import { Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router";
import { toast } from "sonner";
import { PageHeader } from "@/components/shared/PageHeader";
import { ErrorState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { mailboxAddress } from "@/lib/schemas/mail";
import { useDeleteMailbox, useMailboxes } from "@/features/mail/api";
import { MobileCard, MobileCardRow, ResponsiveTable } from "@/components/shared/ResponsiveTable";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { apiRequest } from "@/lib/api-client";
import { mailboxSchema } from "@/lib/schemas/mail";
import { useQueryClient } from "@tanstack/react-query";

export default function MailboxesPage() {
  const { data, isLoading, isError, refetch } = useMailboxes();
  const qc = useQueryClient();
  const deleteMailbox = useDeleteMailbox();
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");

  const createMailbox = async () => {
    const created = await apiRequest("/mail/mailboxes", mailboxSchema, {
      method: "POST",
      body: { name, address: email },
    });
    toast.success(`Inbox ${created.name} created`);
    setOpen(false);
    setName("");
    setEmail("");
    void qc.invalidateQueries({ queryKey: ["mailboxes"] });
  };

  if (isLoading) {
    return (
      <div className="space-y-4 p-6">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64" />
      </div>
    );
  }

  if (isError || !data) {
    return <ErrorState message="Failed to load mailboxes" onRetry={() => void refetch()} />;
  }

  return (
    <div className="space-y-6 p-6">
      <PageHeader
        title="Mailboxes"
        description="Shared inboxes for your support, sales, and operations teams"
        actions={
          <Dialog open={open} onOpenChange={setOpen}>
            <DialogTrigger asChild>
              <Button>
                <Plus className="h-4 w-4" aria-hidden="true" />
                New inbox
              </Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader><DialogTitle>Create shared inbox</DialogTitle></DialogHeader>
              <div className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="mb-name">Display name</Label>
                  <Input id="mb-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="Support" />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="mb-email">Email address</Label>
                  <Input id="mb-email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="support@yourcompany.com" />
                </div>
              </div>
              <DialogFooter>
                <Button onClick={() => void createMailbox()} disabled={!name || !email}>Create inbox</Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        }
      />

      <ResponsiveTable
        mobile={data.map((mb) => (
          <MobileCard key={mb.id}>
            <Link to={`/mailboxes/${mb.id}`} className="font-medium hover:text-primary">
              {mb.name}
            </Link>
            <p className="text-text-muted">{mailboxAddress(mb)}</p>
            <MobileCardRow label="Open threads" value={<Badge variant="secondary">{mb.openThreadCount}</Badge>} />
            <Button
              variant="ghost"
              size="sm"
              className="mt-2 text-destructive"
              aria-label={`Delete ${mb.name}`}
              onClick={() => setDeleteId(mb.id)}
            >
              <Trash2 className="h-4 w-4" /> Delete
            </Button>
          </MobileCard>
        ))}
      >
        <div className="rounded-lg border border-border">
          <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Email</TableHead>
              <TableHead>Members</TableHead>
              <TableHead>Open</TableHead>
              <TableHead className="w-24" />
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.map((mb) => (
              <TableRow key={mb.id}>
                <TableCell className="font-medium">
                  <Link to={`/mailboxes/${mb.id}`} className="hover:text-primary hover:underline">
                    {mb.name}
                  </Link>
                </TableCell>
                <TableCell className="text-text-muted">{mailboxAddress(mb)}</TableCell>
                <TableCell>—</TableCell>
                <TableCell>
                  <Badge variant="secondary">{mb.openThreadCount}</Badge>
                </TableCell>
                <TableCell>
                  <Button
                    variant="ghost"
                    size="icon"
                    aria-label={`Delete ${mb.name}`}
                    onClick={() => setDeleteId(mb.id)}
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        </div>
      </ResponsiveTable>

      <Dialog open={!!deleteId} onOpenChange={() => setDeleteId(null)}>
        <DialogContent>
          <DialogHeader><DialogTitle>Delete mailbox?</DialogTitle></DialogHeader>
          <p className="text-sm text-text-muted">This cannot be undone.</p>
          <DialogFooter>
            <Button
              variant="destructive"
              disabled={deleteMailbox.isPending}
              onClick={() => {
                if (!deleteId) return;
                void deleteMailbox
                  .mutateAsync(deleteId)
                  .then(() => {
                    toast.success("Mailbox deleted");
                    setDeleteId(null);
                  })
                  .catch(() => toast.error("Delete failed"));
              }}
            >
              Delete
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
