import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { toast } from "sonner";
import { PageHeader } from "@/components/shared/PageHeader";
import { EmptyState, ErrorState } from "@/components/shared/states";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { getApiErrorMessage } from "@/lib/api-client";
import { PERMISSIONS } from "@/lib/permissions";
import type { AiPrompt } from "@/lib/schemas/ai";
import {
  useAiPrompts,
  useAiSettings,
  useAiStatus,
  useUpdateAiPrompt,
  useUpdateAiSettings,
} from "@/features/ai/api";
import { AiQuotaHint, AiUnavailableHint } from "@/features/ai/components/AiUnavailableHint";

const PROVIDERS = [
  { value: "gemini", label: "Google Gemini" },
  { value: "openai", label: "OpenAI" },
  { value: "anthropic", label: "Anthropic" },
] as const;

function groupPrompts(prompts: AiPrompt[]) {
  const byKey = new Map<string, { platform?: AiPrompt; override?: AiPrompt }>();
  for (const p of prompts) {
    const entry = byKey.get(p.taskKey) ?? {};
    if (p.orgOverride) entry.override = p;
    else entry.platform = p;
    byKey.set(p.taskKey, entry);
  }
  return [...byKey.entries()].sort(([a], [b]) => a.localeCompare(b));
}

export default function AiSettingsPage() {
  const statusQuery = useAiStatus();
  const settingsQuery = useAiSettings();
  const promptsQuery = useAiPrompts();
  const updateSettings = useUpdateAiSettings();
  const updatePrompt = useUpdateAiPrompt();

  const [provider, setProvider] = useState("");
  const [chatModel, setChatModel] = useState("");
  const [reasoningModel, setReasoningModel] = useState("");
  const [firstResponder, setFirstResponder] = useState(false);
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const [editTemplate, setEditTemplate] = useState("");
  const [editProvider, setEditProvider] = useState("");
  const [editModel, setEditModel] = useState("");
  const [editTemperature, setEditTemperature] = useState("0.7");

  useEffect(() => {
    if (settingsQuery.data) {
      setProvider(settingsQuery.data.preferredProvider ?? "");
      setChatModel(settingsQuery.data.preferredChatModel ?? "");
      setReasoningModel(settingsQuery.data.preferredReasoningModel ?? "");
      setFirstResponder(settingsQuery.data.firstResponderEnabled);
    }
  }, [settingsQuery.data]);

  const promptGroups = useMemo(
    () => groupPrompts(promptsQuery.data ?? []),
    [promptsQuery.data],
  );

  const openPromptEditor = (taskKey: string, platform?: AiPrompt, override?: AiPrompt) => {
    const source = override ?? platform;
    if (!source) return;
    setEditingKey(taskKey);
    setEditTemplate(source.template);
    setEditProvider(source.provider ?? "");
    setEditModel(source.model ?? "");
    setEditTemperature(String(source.temperature));
  };

  const saveSettings = async () => {
    try {
      await updateSettings.mutateAsync({
        preferredProvider: provider || null,
        preferredChatModel: chatModel || null,
        preferredReasoningModel: reasoningModel || null,
        firstResponderEnabled: firstResponder,
      });
      toast.success("AI settings saved");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  const savePrompt = async () => {
    if (!editingKey) return;
    try {
      await updatePrompt.mutateAsync({
        taskKey: editingKey,
        template: editTemplate,
        provider: editProvider || undefined,
        model: editModel || undefined,
        temperature: parseFloat(editTemperature) || undefined,
      });
      toast.success("Prompt override saved");
      setEditingKey(null);
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  const revertPrompt = async (taskKey: string, platform?: AiPrompt) => {
    if (!platform) {
      toast.error("No platform default found for this task");
      return;
    }
    try {
      await updatePrompt.mutateAsync({
        taskKey,
        template: platform.template,
        provider: platform.provider ?? undefined,
        model: platform.model ?? undefined,
        temperature: platform.temperature,
      });
      toast.success("Reverted to platform default content");
      setEditingKey(null);
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  return (
    <PermissionGate
      permission={PERMISSIONS.AI_CONFIGURE}
      fallback={
        <div className="p-6">
          <PageHeader title="AI settings" description="You do not have permission to configure AI." />
        </div>
      }
    >
      <div className="mx-auto max-w-4xl space-y-6 p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] sm:p-6">
        <PageHeader
          title="AI settings"
          description="Provider preferences, first-responder automation, and prompt overrides."
        />

        {statusQuery.data && (
          <div className="rounded-lg border border-border bg-surface-muted/30 p-4">
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant={statusQuery.data.configured ? "success" : "secondary"}>
                {statusQuery.data.configured ? "Configured" : "Not configured"}
              </Badge>
              <Badge variant={statusQuery.data.enabled ? "success" : "secondary"}>
                {statusQuery.data.enabled ? "Enabled" : "Disabled"}
              </Badge>
              <span className="text-sm text-text-muted">
                {statusQuery.data.tokensUsedThisMonth.toLocaleString()} /{" "}
                {statusQuery.data.monthlyQuota.toLocaleString()} tokens this month
              </span>
            </div>
            <AiUnavailableHint status={statusQuery.data} className="mt-2" />
            <AiQuotaHint status={statusQuery.data} className="mt-1" />
            <p className="mt-2 text-xs text-text-muted">
              Provider API keys are configured on the server. Contact your platform administrator if AI shows as not configured.
            </p>
            <Button variant="link" size="sm" className="mt-1 h-auto p-0" asChild>
              <Link to="/ai/usage">View usage & costs</Link>
            </Button>
          </div>
        )}

        {settingsQuery.isError ? (
          <ErrorState message="Failed to load AI settings" onRetry={() => void settingsQuery.refetch()} />
        ) : (
          <Tabs defaultValue="general">
            <TabsList className="w-full flex-wrap h-auto">
              <TabsTrigger value="general">General</TabsTrigger>
              <TabsTrigger value="prompts">Prompt overrides</TabsTrigger>
            </TabsList>

            <TabsContent value="general" className="mt-4 space-y-4">
              {settingsQuery.isLoading ? (
                <Skeleton className="h-48" />
              ) : (
                <>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <div className="space-y-2">
                      <Label htmlFor="ai-provider">Preferred provider</Label>
                      <Select value={provider || "default"} onValueChange={(v) => setProvider(v === "default" ? "" : v)}>
                        <SelectTrigger id="ai-provider">
                          <SelectValue placeholder="Platform default" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="default">Platform default</SelectItem>
                          {PROVIDERS.map((p) => (
                            <SelectItem key={p.value} value={p.value}>{p.label}</SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="ai-chat-model">Chat model</Label>
                      <Input
                        id="ai-chat-model"
                        value={chatModel}
                        onChange={(e) => setChatModel(e.target.value)}
                        placeholder="e.g. gpt-4o-mini"
                      />
                    </div>
                    <div className="space-y-2 sm:col-span-2">
                      <Label htmlFor="ai-reasoning-model">Reasoning model</Label>
                      <Input
                        id="ai-reasoning-model"
                        value={reasoningModel}
                        onChange={(e) => setReasoningModel(e.target.value)}
                        placeholder="e.g. claude-sonnet"
                      />
                    </div>
                  </div>

                  <div className="flex flex-col gap-4 rounded-lg border border-border p-4 sm:flex-row sm:items-center sm:justify-between">
                    <div className="min-w-0">
                      <p className="font-medium">First responder</p>
                      <p className="text-sm text-text-muted">
                        Automatically draft replies to new visitor messages before an agent responds.
                      </p>
                    </div>
                    <Switch checked={firstResponder} onCheckedChange={setFirstResponder} aria-label="First responder" />
                  </div>

                  <Button onClick={() => void saveSettings()} disabled={updateSettings.isPending}>
                    Save preferences
                  </Button>
                </>
              )}
            </TabsContent>

            <TabsContent value="prompts" className="mt-4 space-y-3">
              {promptsQuery.isLoading ? (
                <Skeleton className="h-48" />
              ) : promptsQuery.isError ? (
                <ErrorState message="Failed to load prompts" onRetry={() => void promptsQuery.refetch()} />
              ) : promptGroups.length === 0 ? (
                <EmptyState
                  className="py-8"
                  title="No prompts"
                  description="Platform prompts appear here when AI tasks are configured."
                />
              ) : (
                promptGroups.map(([taskKey, { platform, override }]) => (
                  <div key={taskKey} className="rounded-lg border border-border p-4">
                    <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                      <div className="min-w-0">
                        <p className="font-medium">{platform?.name ?? taskKey}</p>
                        <p className="text-xs text-text-muted">{platform?.description ?? taskKey}</p>
                        <div className="mt-1 flex flex-wrap gap-1">
                          {override ? (
                            <Badge variant="warning" className="text-[10px]">Org override</Badge>
                          ) : (
                            <Badge variant="secondary" className="text-[10px]">Platform default</Badge>
                          )}
                        </div>
                      </div>
                      <div className="flex shrink-0 flex-wrap gap-2">
                        <Button size="sm" variant="outline" onClick={() => openPromptEditor(taskKey, platform, override)}>
                          {override ? "Edit override" : "Create override"}
                        </Button>
                        {override && platform && (
                          <Button size="sm" variant="ghost" onClick={() => void revertPrompt(taskKey, platform)}>
                            Revert
                          </Button>
                        )}
                      </div>
                    </div>
                  </div>
                ))
              )}
            </TabsContent>
          </Tabs>
        )}

        {editingKey && (
          <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/50 p-0 sm:items-center sm:p-4">
            <div className="flex max-h-[90dvh] w-full max-w-2xl flex-col rounded-t-lg border border-border bg-surface sm:rounded-lg">
              <div className="border-b border-border p-4">
                <h3 className="font-semibold">Edit prompt: {editingKey}</h3>
              </div>
              <div className="flex-1 space-y-4 overflow-y-auto p-4">
                <div className="space-y-2">
                  <Label htmlFor="prompt-template">Template</Label>
                  <Textarea
                    id="prompt-template"
                    value={editTemplate}
                    onChange={(e) => setEditTemplate(e.target.value)}
                    className="min-h-[200px] font-mono text-xs"
                  />
                </div>
                <div className="grid gap-4 sm:grid-cols-3">
                  <div className="space-y-2">
                    <Label htmlFor="prompt-provider">Provider override</Label>
                    <Input id="prompt-provider" value={editProvider} onChange={(e) => setEditProvider(e.target.value)} />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="prompt-model">Model override</Label>
                    <Input id="prompt-model" value={editModel} onChange={(e) => setEditModel(e.target.value)} />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="prompt-temp">Temperature</Label>
                    <Input
                      id="prompt-temp"
                      type="number"
                      min={0}
                      max={2}
                      step={0.1}
                      value={editTemperature}
                      onChange={(e) => setEditTemperature(e.target.value)}
                    />
                  </div>
                </div>
              </div>
              <div className="flex justify-end gap-2 border-t border-border p-4 pb-[calc(1rem+env(safe-area-inset-bottom))]">
                <Button variant="ghost" onClick={() => setEditingKey(null)}>Cancel</Button>
                <Button onClick={() => void savePrompt()} disabled={updatePrompt.isPending}>Save override</Button>
              </div>
            </div>
          </div>
        )}
      </div>
    </PermissionGate>
  );
}
