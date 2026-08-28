import { Loader2, Sparkles, Tags } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { getApiErrorMessage } from "@/lib/api-client";
import { hasCachedTriage, type AiTriageSuggestion } from "@/lib/schemas/ai";
import { PERMISSIONS } from "@/lib/permissions";
import {
  useAiStatus,
  useMailSummarizeThread,
  useMailThreadTriage,
  useMailTriageThread,
} from "@/features/ai/api";
import { AiUnavailableHint } from "./AiUnavailableHint";
import { useState } from "react";

interface MailThreadAiPanelProps {
  threadId: string;
}

export function MailThreadAiPanel({ threadId }: MailThreadAiPanelProps) {
  const statusQuery = useAiStatus();
  const triageQuery = useMailThreadTriage(threadId);
  const summarize = useMailSummarizeThread();
  const runTriage = useMailTriageThread();
  const [summaryOpen, setSummaryOpen] = useState(false);
  const [summaryText, setSummaryText] = useState("");

  const status = statusQuery.data;
  const triage = triageQuery.data;
  const canUseAi = status?.enabled && status?.configured;
  const showTriage = triage && hasCachedTriage(triage);

  const handleSummarize = async () => {
    try {
      const result = await summarize.mutateAsync(threadId);
      if (!result.available) {
        toast.error("AI is not available. Configure a provider in AI settings.");
        return;
      }
      setSummaryText(result.text);
      setSummaryOpen(true);
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  const handleTriage = async () => {
    try {
      const result = await runTriage.mutateAsync(threadId);
      if (!result.available) {
        toast.error("AI is not available. Configure a provider in AI settings.");
      }
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  return (
    <>
      <div className="flex flex-col gap-2 border-b border-border bg-surface-muted/30 px-4 py-2 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-wrap items-center gap-2">
          <Sparkles className="h-4 w-4 text-primary" aria-hidden="true" />
          <span className="text-xs font-medium uppercase tracking-wide text-text-muted">AI insights</span>
          {showTriage && <TriageBadges triage={triage} />}
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <PermissionGate permission={PERMISSIONS.AI_USE}>
            <Button
              size="sm"
              variant="outline"
              disabled={!canUseAi || summarize.isPending}
              onClick={() => void handleSummarize()}
            >
              {summarize.isPending ? (
                <Loader2 className="h-3 w-3 animate-spin" />
              ) : (
                "Summarize"
              )}
            </Button>
            {!showTriage && (
              <Button
                size="sm"
                variant="outline"
                className="gap-1"
                disabled={!canUseAi || runTriage.isPending}
                onClick={() => void handleTriage()}
              >
                {runTriage.isPending ? (
                  <Loader2 className="h-3 w-3 animate-spin" />
                ) : (
                  <Tags className="h-3 w-3" />
                )}
                Triage
              </Button>
            )}
          </PermissionGate>
        </div>

        <AiUnavailableHint status={status} className="sm:w-auto w-full" compact />
      </div>

      <Dialog open={summaryOpen} onOpenChange={setSummaryOpen}>
        <DialogContent className="max-h-[85dvh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Thread summary</DialogTitle>
          </DialogHeader>
          <p className="whitespace-pre-wrap text-sm">{summaryText}</p>
        </DialogContent>
      </Dialog>
    </>
  );
}

function TriageBadges({ triage }: { triage: AiTriageSuggestion }) {
  return (
    <div className="flex flex-wrap items-center gap-1">
      {triage.intent && (
        <Badge variant="secondary" className="text-[10px]">
          {triage.intent}
        </Badge>
      )}
      {triage.suggestedPriority && (
        <Badge variant="outline" className="text-[10px]">
          {triage.suggestedPriority}
        </Badge>
      )}
      {triage.suggestedTags.map((tag) => (
        <Badge key={tag} variant="outline" className="text-[10px]">
          {tag}
        </Badge>
      ))}
      {triage.confidence != null && (
        <span className="text-[10px] text-text-muted">
          {Math.round(triage.confidence * 100)}% conf.
        </span>
      )}
    </div>
  );
}
