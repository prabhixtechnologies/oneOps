import { Loader2, Sparkles, Square, Wand2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import type { AiAvailability } from "@/lib/schemas/ai";
import { AiUnavailableHint } from "./AiUnavailableHint";

interface StreamSuggestControlsProps {
  status: AiAvailability | undefined;
  streaming: boolean;
  hasPermission: boolean;
  onSuggest: () => void;
  onCancel: () => void;
  className?: string;
  label?: string;
}

export function StreamSuggestControls({
  status,
  streaming,
  hasPermission,
  onSuggest,
  onCancel,
  className,
  label = "Suggest reply",
}: StreamSuggestControlsProps) {
  if (!hasPermission) return null;

  const canUse = status?.enabled && status?.configured;

  return (
    <div className={cn("flex flex-col gap-1", className)}>
      <div className="flex flex-wrap items-center gap-1">
        <Button
          type="button"
          size="sm"
          variant="outline"
          disabled={!canUse || streaming}
          onClick={onSuggest}
          className="gap-1.5"
        >
          {streaming ? (
            <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" />
          ) : (
            <Sparkles className="h-3.5 w-3.5" aria-hidden="true" />
          )}
          {streaming ? "Generating…" : label}
        </Button>
        {streaming && (
          <Button type="button" size="sm" variant="ghost" onClick={onCancel} className="gap-1">
            <Square className="h-3 w-3" aria-hidden="true" />
            Stop
          </Button>
        )}
      </div>
      {!canUse && <AiUnavailableHint status={status} compact />}
    </div>
  );
}

interface RewriteDraftMenuProps {
  status: AiAvailability | undefined;
  hasPermission: boolean;
  disabled?: boolean;
  pending?: boolean;
  onRewrite: (action: string) => void;
}

const REWRITE_ACTIONS = [
  { action: "Improve", label: "Improve tone" },
  { action: "Shorten", label: "Shorten" },
  { action: "Translate", label: "Translate to English" },
] as const;

export function RewriteDraftMenu({
  status,
  hasPermission,
  disabled,
  pending,
  onRewrite,
}: RewriteDraftMenuProps) {
  if (!hasPermission) return null;
  const canUse = status?.enabled && status?.configured;

  return (
    <div className="flex flex-wrap items-center gap-1">
      {REWRITE_ACTIONS.map(({ action, label }) => (
        <Button
          key={action}
          type="button"
          size="sm"
          variant="ghost"
          disabled={!canUse || disabled || pending}
          onClick={() => onRewrite(action)}
          className="gap-1 text-xs"
        >
          {pending ? (
            <Loader2 className="h-3 w-3 animate-spin" aria-hidden="true" />
          ) : (
            <Wand2 className="h-3 w-3" aria-hidden="true" />
          )}
          {label}
        </Button>
      ))}
      {!canUse && <AiUnavailableHint status={status} compact className="w-full" />}
    </div>
  );
}
