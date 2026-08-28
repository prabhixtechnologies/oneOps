import { Link } from "react-router";
import { Sparkles } from "lucide-react";
import { cn } from "@/lib/utils";
import type { AiAvailability } from "@/lib/schemas/ai";

interface AiUnavailableHintProps {
  status: AiAvailability | undefined;
  className?: string;
  compact?: boolean;
}

export function AiUnavailableHint({ status, className, compact }: AiUnavailableHintProps) {
  if (!status) return null;

  if (!status.enabled) {
    return (
      <p className={cn("text-xs text-text-muted", className)}>
        AI is disabled for this organization.
      </p>
    );
  }

  if (!status.configured) {
    return (
      <p className={cn("text-xs text-text-muted", className)}>
        {compact ? (
          <>
            AI not configured.{" "}
            <Link to="/ai/settings" className="text-primary underline-offset-2 hover:underline">
              Set up
            </Link>
          </>
        ) : (
          <>
            Connect an AI provider to enable suggestions.{" "}
            <Link
              to="/ai/settings"
              className="inline-flex items-center gap-1 text-primary underline-offset-2 hover:underline"
            >
              <Sparkles className="h-3 w-3" aria-hidden="true" />
              Configure AI
            </Link>
          </>
        )}
      </p>
    );
  }

  return null;
}

interface AiQuotaHintProps {
  status: AiAvailability | undefined;
  className?: string;
}

export function AiQuotaHint({ status, className }: AiQuotaHintProps) {
  if (!status?.configured || status.monthlyQuota <= 0) return null;
  const pct = Math.min(100, Math.round((status.tokensUsedThisMonth / status.monthlyQuota) * 100));
  if (pct < 80) return null;
  return (
    <p className={cn("text-xs text-warning", className)}>
      {pct >= 100
        ? "Monthly AI token quota reached."
        : `${pct}% of monthly AI token quota used.`}
    </p>
  );
}
