import { Loader2, Sparkles } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { PERMISSIONS } from "@/lib/permissions";
import { useAiStatus, useChatHandoffSummary, useChatSentiment } from "@/features/ai/api";
import { AiUnavailableHint } from "./AiUnavailableHint";
import { toast } from "sonner";
import { getApiErrorMessage } from "@/lib/api-client";

interface ChatConversationAiPanelProps {
  conversationId: string;
}

function sentimentVariant(sentiment: string | null | undefined): "success" | "secondary" | "destructive" | "warning" {
  const s = sentiment?.toLowerCase();
  if (s === "positive" || s === "happy") return "success";
  if (s === "negative" || s === "angry" || s === "frustrated") return "destructive";
  if (s === "neutral") return "secondary";
  return "warning";
}

function urgencyVariant(urgency: string | null | undefined): "destructive" | "warning" | "secondary" {
  const u = urgency?.toLowerCase();
  if (u === "high" || u === "urgent" || u === "critical") return "destructive";
  if (u === "medium" || u === "moderate") return "warning";
  return "secondary";
}

export function ChatConversationAiPanel({ conversationId }: ChatConversationAiPanelProps) {
  const statusQuery = useAiStatus();
  const sentimentQuery = useChatSentiment(conversationId);
  const handoff = useChatHandoffSummary();
  const status = statusQuery.data;
  const sentiment = sentimentQuery.data;
  const canUseAi = status?.enabled && status?.configured;

  const handleHandoff = async () => {
    try {
      const result = await handoff.mutateAsync(conversationId);
      if (!result.available) {
        toast.error("AI is not available. Configure a provider in AI settings.");
        return;
      }
      toast.success("Handoff summary saved as internal note");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  return (
    <div className="flex flex-col gap-2 border-b border-border bg-surface-muted/30 px-4 py-2">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <Sparkles className="h-4 w-4 text-primary" aria-hidden="true" />
          <span className="text-xs font-medium uppercase tracking-wide text-text-muted">AI insights</span>
          {sentimentQuery.isLoading && <Loader2 className="h-3 w-3 animate-spin text-text-muted" />}
          {sentiment?.available && sentiment.sentiment && (
            <Badge variant={sentimentVariant(sentiment.sentiment)} className="text-[10px] capitalize">
              {sentiment.sentiment}
            </Badge>
          )}
          {sentiment?.available && sentiment.urgency && (
            <Badge variant={urgencyVariant(sentiment.urgency)} className="text-[10px] capitalize">
              {sentiment.urgency} urgency
            </Badge>
          )}
        </div>

        <PermissionGate permission={PERMISSIONS.AI_USE}>
          <Button
            size="sm"
            variant="outline"
            disabled={!canUseAi || handoff.isPending}
            onClick={() => void handleHandoff()}
          >
            {handoff.isPending ? <Loader2 className="h-3 w-3 animate-spin" /> : "Handoff summary"}
          </Button>
        </PermissionGate>
      </div>

      {sentiment?.available && sentiment.summary && (
        <p className="text-xs text-text-muted">{sentiment.summary}</p>
      )}

      <AiUnavailableHint status={status} compact />
    </div>
  );
}
