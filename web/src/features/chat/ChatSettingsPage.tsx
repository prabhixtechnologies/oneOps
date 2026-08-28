import { Plus, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router";
import { toast } from "sonner";
import { PageHeader } from "@/components/shared/PageHeader";
import { ErrorState } from "@/components/shared/states";
import { PermissionGate } from "@/components/shared/PermissionGate";
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import {
  useChatCannedReplies,
  useChatSettings,
  useCreateChatCannedReply,
  useDeleteChatCannedReply,
  useUpdateChatSettings,
} from "@/features/chat/api";
import { useMailboxes } from "@/features/mail/api";
import { getApiErrorMessage } from "@/lib/api-client";
import { PERMISSIONS } from "@/lib/permissions";

export default function ChatSettingsPage() {
  const settingsQuery = useChatSettings();
  const updateSettings = useUpdateChatSettings();
  const mailboxesQuery = useMailboxes();
  const cannedQuery = useChatCannedReplies();
  const createCanned = useCreateChatCannedReply();
  const deleteCanned = useDeleteChatCannedReply();

  const [availability, setAvailability] = useState("ONLINE");
  const [awayMessage, setAwayMessage] = useState("");
  const [preChatEnabled, setPreChatEnabled] = useState(true);
  const [offlineMailboxId, setOfflineMailboxId] = useState("");
  const [businessHoursJson, setBusinessHoursJson] = useState("{}");
  const [cannedOpen, setCannedOpen] = useState(false);
  const [cannedTitle, setCannedTitle] = useState("");
  const [cannedBody, setCannedBody] = useState("");
  const [cannedShortcut, setCannedShortcut] = useState("");

  useEffect(() => {
    if (settingsQuery.data) {
      setAvailability(settingsQuery.data.availability ?? "ONLINE");
      setAwayMessage(settingsQuery.data.awayMessage ?? "");
      setPreChatEnabled(settingsQuery.data.preChatEnabled ?? true);
      setOfflineMailboxId(settingsQuery.data.offlineMailboxId ?? "");
      setBusinessHoursJson(
        JSON.stringify(settingsQuery.data.businessHours ?? {}, null, 2),
      );
    }
  }, [settingsQuery.data]);

  const saveSettings = async () => {
    let businessHours: Record<string, unknown> = {};
    try {
      businessHours = JSON.parse(businessHoursJson) as Record<string, unknown>;
    } catch {
      toast.error("Business hours must be valid JSON");
      return;
    }
    try {
      await updateSettings.mutateAsync({
        availability,
        awayMessage,
        preChatEnabled,
        offlineMailboxId: offlineMailboxId || null,
        businessHours,
      });
      toast.success("Chat settings saved");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  const onCreateCanned = async () => {
    try {
      await createCanned.mutateAsync({
        title: cannedTitle,
        body: cannedBody,
        shortcut: cannedShortcut || undefined,
      });
      toast.success("Canned reply created");
      setCannedOpen(false);
      setCannedTitle("");
      setCannedBody("");
      setCannedShortcut("");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  if (settingsQuery.isLoading) {
    return <div className="p-6"><Skeleton className="h-96" /></div>;
  }

  if (settingsQuery.isError) {
    return <ErrorState message="Failed to load chat settings" onRetry={() => void settingsQuery.refetch()} />;
  }

  return (
    <div className="space-y-6 p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] md:p-6">
      <PageHeader
        title="Chat settings"
        description="Availability, business hours, and canned replies for live chat"
        actions={
          <Button variant="outline" asChild>
            <Link to="/chat">Back to chat</Link>
          </Button>
        }
      />

      <PermissionGate permission={PERMISSIONS.CHAT_MANAGE}>
        <div className="max-w-xl space-y-4 rounded-lg border border-border p-4">
          <div className="space-y-2">
            <Label htmlFor="availability">Availability</Label>
            <Select value={availability} onValueChange={setAvailability}>
              <SelectTrigger id="availability"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="ONLINE">Online</SelectItem>
                <SelectItem value="AWAY">Away</SelectItem>
                <SelectItem value="OFFLINE">Offline</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label htmlFor="away-message">Away message</Label>
            <Textarea
              id="away-message"
              value={awayMessage}
              onChange={(e) => setAwayMessage(e.target.value)}
              placeholder="Shown to visitors when agents are away"
            />
          </div>

          <div className="flex items-center justify-between">
            <Label htmlFor="pre-chat">Pre-chat form</Label>
            <Switch id="pre-chat" checked={preChatEnabled} onCheckedChange={setPreChatEnabled} />
          </div>

          <div className="space-y-2">
            <Label htmlFor="offline-mailbox">Offline mailbox</Label>
            <Select value={offlineMailboxId || "none"} onValueChange={(v) => setOfflineMailboxId(v === "none" ? "" : v)}>
              <SelectTrigger id="offline-mailbox"><SelectValue placeholder="Select mailbox" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="none">None</SelectItem>
                {(mailboxesQuery.data ?? []).map((mb) => (
                  <SelectItem key={mb.id} value={mb.id}>{mb.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label htmlFor="business-hours">Business hours (JSON)</Label>
            <Textarea
              id="business-hours"
              value={businessHoursJson}
              onChange={(e) => setBusinessHoursJson(e.target.value)}
              className="min-h-[120px] font-mono text-xs"
            />
          </div>

          <Button onClick={() => void saveSettings()} disabled={updateSettings.isPending}>
            Save settings
          </Button>
        </div>

        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold">Canned replies</h2>
            <Button onClick={() => setCannedOpen(true)}>
              <Plus className="h-4 w-4" /> New reply
            </Button>
          </div>

          {cannedQuery.isLoading ? (
            <Skeleton className="h-32" />
          ) : (cannedQuery.data?.length ?? 0) === 0 ? (
            <p className="text-sm text-text-muted">No canned replies yet.</p>
          ) : (
            <div className="space-y-3">
              {cannedQuery.data?.map((cr) => (
                <div key={cr.id} className="flex items-start justify-between gap-4 rounded-lg border border-border p-4">
                  <div>
                    <p className="font-medium">{cr.title}</p>
                    {cr.shortcut && <code className="text-xs text-text-muted">/{cr.shortcut}</code>}
                    <p className="mt-2 line-clamp-2 text-sm text-text-muted">{cr.body}</p>
                  </div>
                  <Button
                    variant="ghost"
                    size="icon"
                    aria-label={`Delete ${cr.title}`}
                    disabled={deleteCanned.isPending}
                    onClick={() =>
                      void deleteCanned
                        .mutateAsync(cr.id)
                        .then(() => toast.success("Canned reply deleted"))
                        .catch((err) => toast.error(getApiErrorMessage(err)))
                    }
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              ))}
            </div>
          )}
        </div>
      </PermissionGate>

      <Dialog open={cannedOpen} onOpenChange={setCannedOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>New canned reply</DialogTitle></DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label>Title</Label>
              <Input value={cannedTitle} onChange={(e) => setCannedTitle(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>Shortcut</Label>
              <Input value={cannedShortcut} onChange={(e) => setCannedShortcut(e.target.value)} placeholder="hello" />
            </div>
            <div className="space-y-2">
              <Label>Body</Label>
              <Textarea value={cannedBody} onChange={(e) => setCannedBody(e.target.value)} className="min-h-[100px]" />
            </div>
          </div>
          <DialogFooter>
            <Button onClick={() => void onCreateCanned()} disabled={createCanned.isPending || !cannedTitle || !cannedBody}>
              Create
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
