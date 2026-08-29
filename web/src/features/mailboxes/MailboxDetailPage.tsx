import { Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import { useFieldArray, useForm } from "react-hook-form";
import { useParams } from "react-router";
import { toast } from "sonner";
import { PageHeader } from "@/components/shared/PageHeader";
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import type { RoutingRule } from "@/lib/schemas/mail";
import {
  useAddMailboxMember,
  useMailbox,
  useRemoveMailboxMember,
  useUpdateMailbox,
  useUpdateRoutingRules,
} from "@/features/mail/api";
import { useMembers } from "@/features/org/api";
import { getApiErrorMessage } from "@/lib/api-client";
import { MailClientPanel } from "./MailClientPanel";

const CONDITION_FIELDS = ["FROM", "FROM_DOMAIN", "TO", "SUBJECT", "BODY", "HAS_ATTACHMENT", "SPAM_SCORE"] as const;
const OPERATORS = ["EQUALS", "CONTAINS", "MATCHES", "IN", "GT", "LT"] as const;
const ACTION_TYPES = ["ASSIGN_USER", "ASSIGN_TEAM", "SET_PRIORITY", "ADD_TAG", "APPLY_SLA", "SET_STATUS"] as const;

export default function MailboxDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { data, isLoading, isError, refetch } = useMailbox(id);
  const updateMailbox = useUpdateMailbox();
  const updateRules = useUpdateRoutingRules();
  const addMember = useAddMailboxMember();
  const removeMember = useRemoveMailboxMember();
  const membersQuery = useMembers("");
  const [addMemberOpen, setAddMemberOpen] = useState(false);
  const [newMemberUserId, setNewMemberUserId] = useState("");
  const orgMembers = membersQuery.data?.pages.flatMap((p) => p.items) ?? [];

  const rulesForm = useForm<{ rules: RoutingRule[] }>({
    values: data ? { rules: data.routingRules ?? [] } : undefined,
  });
  const { fields, append, remove } = useFieldArray({ control: rulesForm.control, name: "rules" });

  if (isLoading) {
    return <div className="p-6"><Skeleton className="h-96" /></div>;
  }

  if (isError || !data) {
    return <ErrorState message="Failed to load mailbox" onRetry={() => void refetch()} />;
  }

  const saveSettings = async (field: string, value: string) => {
    await updateMailbox.mutateAsync({ id: data.id, data: { [field]: value } });
    toast.success("Mailbox updated");
  };

  const saveRules = rulesForm.handleSubmit(async (formData) => {
    await updateRules.mutateAsync({ id: data.id, rules: formData.rules });
    toast.success("Routing rules saved");
  });

  return (
    <div className="space-y-6 p-6">
      <PageHeader title={data.name} description={data.email ?? data.address ?? ""} />

      <Tabs defaultValue="general">
        <TabsList>
          <TabsTrigger value="general">General</TabsTrigger>
          <TabsTrigger value="members">Members</TabsTrigger>
          <TabsTrigger value="routing">Routing rules</TabsTrigger>
          <TabsTrigger value="sla">SLA & hours</TabsTrigger>
          <TabsTrigger value="client">Mail client</TabsTrigger>
        </TabsList>

        <TabsContent value="general" className="mt-4 max-w-xl space-y-4">
          <div className="space-y-2">
            <Label htmlFor="signature">Email signature</Label>
            <Textarea
              id="signature"
              defaultValue={data.signature ?? ""}
              onBlur={(e) => void saveSettings("signature", e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="description">Description</Label>
            <Input
              id="description"
              defaultValue={data.description ?? ""}
              onBlur={(e) => void saveSettings("description", e.target.value)}
            />
          </div>
        </TabsContent>

        <TabsContent value="members" className="mt-4">
          <ul className="divide-y divide-border rounded-lg border border-border">
            {(data.members ?? []).map((m) => (
              <li key={m.userId} className="flex items-center justify-between px-4 py-3 text-sm">
                <div>
                  <p className="font-medium">{m.name}</p>
                  <p className="text-text-muted">{m.email}</p>
                </div>
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={removeMember.isPending}
                  onClick={() =>
                    void removeMember
                      .mutateAsync({ mailboxId: data.id, userId: m.userId })
                      .then(() => toast.success("Member removed"))
                      .catch((err) => toast.error(getApiErrorMessage(err)))
                  }
                >
                  Remove
                </Button>
              </li>
            ))}
          </ul>
          <Button className="mt-4" variant="outline" onClick={() => setAddMemberOpen(true)}>
            Add member
          </Button>

          <Dialog open={addMemberOpen} onOpenChange={setAddMemberOpen}>
            <DialogContent>
              <DialogHeader><DialogTitle>Add mailbox member</DialogTitle></DialogHeader>
              <Select value={newMemberUserId} onValueChange={setNewMemberUserId}>
                <SelectTrigger><SelectValue placeholder="Select member" /></SelectTrigger>
                <SelectContent>
                  {orgMembers.map((m) => (
                    <SelectItem key={m.userId} value={m.userId}>{m.displayName} ({m.email})</SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <DialogFooter>
                <Button
                  disabled={!newMemberUserId || addMember.isPending}
                  onClick={() =>
                    void addMember
                      .mutateAsync({ mailboxId: data.id, userId: newMemberUserId })
                      .then(() => {
                        toast.success("Member added");
                        setAddMemberOpen(false);
                        setNewMemberUserId("");
                      })
                      .catch((err) => toast.error(getApiErrorMessage(err)))
                  }
                >
                  Add
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </TabsContent>

        <TabsContent value="routing" className="mt-4 space-y-4">
          <p className="text-sm text-text-muted">
            Rules are evaluated in priority order. First match wins unless Continue is enabled.
          </p>
          <form onSubmit={saveRules} className="space-y-4">
            {fields.map((field, ruleIndex) => (
              <div key={field.id} className="rounded-lg border border-border p-4">
                <div className="mb-3 flex items-center gap-3">
                  <Input
                    {...rulesForm.register(`rules.${ruleIndex}.name`)}
                    placeholder="Rule name"
                    className="flex-1"
                  />
                  <Input
                    type="number"
                    {...rulesForm.register(`rules.${ruleIndex}.priority`, { valueAsNumber: true })}
                    className="w-20"
                    aria-label="Priority"
                  />
                  <div className="flex items-center gap-2">
                    <Switch
                      checked={rulesForm.watch(`rules.${ruleIndex}.enabled`)}
                      onCheckedChange={(c) => rulesForm.setValue(`rules.${ruleIndex}.enabled`, c)}
                    />
                    <span className="text-xs">Enabled</span>
                  </div>
                  <Button type="button" variant="ghost" size="icon" onClick={() => remove(ruleIndex)} aria-label="Delete rule">
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>

                <div className="mb-2 flex items-center gap-2">
                  <Label className="text-xs">Match</Label>
                  <Select
                    value={rulesForm.watch(`rules.${ruleIndex}.match`)}
                    onValueChange={(v: "ALL" | "ANY") => rulesForm.setValue(`rules.${ruleIndex}.match`, v)}
                  >
                    <SelectTrigger className="h-8 w-24"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="ALL">ALL</SelectItem>
                      <SelectItem value="ANY">ANY</SelectItem>
                    </SelectContent>
                  </Select>
                  <Label className="text-xs">conditions</Label>
                </div>

                {(rulesForm.watch(`rules.${ruleIndex}.conditions`) ?? []).map((_, condIndex) => (
                  <div key={condIndex} className="mb-2 flex gap-2">
                    <Select
                      value={rulesForm.watch(`rules.${ruleIndex}.conditions.${condIndex}.field`)}
                      onValueChange={(v) =>
                        rulesForm.setValue(`rules.${ruleIndex}.conditions.${condIndex}.field`, v as typeof CONDITION_FIELDS[number])
                      }
                    >
                      <SelectTrigger className="w-36"><SelectValue /></SelectTrigger>
                      <SelectContent>
                        {CONDITION_FIELDS.map((f) => <SelectItem key={f} value={f}>{f}</SelectItem>)}
                      </SelectContent>
                    </Select>
                    <Select
                      value={rulesForm.watch(`rules.${ruleIndex}.conditions.${condIndex}.op`)}
                      onValueChange={(v) =>
                        rulesForm.setValue(`rules.${ruleIndex}.conditions.${condIndex}.op`, v as typeof OPERATORS[number])
                      }
                    >
                      <SelectTrigger className="w-28"><SelectValue /></SelectTrigger>
                      <SelectContent>
                        {OPERATORS.map((o) => <SelectItem key={o} value={o}>{o}</SelectItem>)}
                      </SelectContent>
                    </Select>
                    <Input
                      {...rulesForm.register(`rules.${ruleIndex}.conditions.${condIndex}.value` as const)}
                      placeholder="Value"
                      className="flex-1"
                    />
                  </div>
                ))}
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={() => {
                    const conditions = rulesForm.getValues(`rules.${ruleIndex}.conditions`);
                    rulesForm.setValue(`rules.${ruleIndex}.conditions`, [
                      ...conditions,
                      { field: "SUBJECT", op: "CONTAINS", value: "" },
                    ]);
                  }}
                >
                  <Plus className="h-3 w-3" /> Add condition
                </Button>

                <Separator className="my-3" />

                <Label className="text-xs">Actions</Label>
                {(rulesForm.watch(`rules.${ruleIndex}.actions`) ?? []).map((_, actIndex) => (
                  <div key={actIndex} className="mt-2 flex gap-2">
                    <Select
                      value={rulesForm.watch(`rules.${ruleIndex}.actions.${actIndex}.type`)}
                      onValueChange={(v) =>
                        rulesForm.setValue(`rules.${ruleIndex}.actions.${actIndex}.type`, v as typeof ACTION_TYPES[number])
                      }
                    >
                      <SelectTrigger className="w-40"><SelectValue /></SelectTrigger>
                      <SelectContent>
                        {ACTION_TYPES.map((a) => <SelectItem key={a} value={a}>{a.replace("_", " ")}</SelectItem>)}
                      </SelectContent>
                    </Select>
                    <Input
                      {...rulesForm.register(`rules.${ruleIndex}.actions.${actIndex}.value`)}
                      placeholder="Value"
                      className="flex-1"
                    />
                  </div>
                ))}
              </div>
            ))}

            <div className="flex gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={() =>
                  append({
                    id: `rule_${Date.now()}`,
                    name: "New rule",
                    priority: 100,
                    conditions: [{ field: "SUBJECT", op: "CONTAINS", value: "" }],
                    match: "ALL",
                    actions: [{ type: "ADD_TAG", value: "" }],
                    continue: false,
                    enabled: true,
                  })
                }
              >
                <Plus className="h-4 w-4" /> Add rule
              </Button>
              <Button type="submit">Save rules</Button>
            </div>
          </form>
        </TabsContent>

        <TabsContent value="sla" className="mt-4 max-w-xl space-y-4">
          {data.businessHours && (
            <>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <Label>Timezone</Label>
                  <p className="text-sm">{data.businessHours.timezone}</p>
                </div>
                <div>
                  <Label>Hours</Label>
                  <p className="text-sm">{data.businessHours.startTime} – {data.businessHours.endTime}</p>
                </div>
              </div>
              <div>
                <Label>Holidays</Label>
                <p className="text-sm text-text-muted">{data.businessHours.holidays.join(", ") || "None configured"}</p>
              </div>
            </>
          )}
        </TabsContent>

        <TabsContent value="client" className="mt-4 max-w-xl">
          <MailClientPanel mailbox={data} />
        </TabsContent>
      </Tabs>
    </div>
  );
}
