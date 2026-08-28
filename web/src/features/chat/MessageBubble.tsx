import { Lock } from "lucide-react";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { RelativeTime } from "@/components/shared/RelativeTime";
import { isInternalNote, type ChatMessage } from "@/lib/schemas/chat";
import { cn } from "@/lib/utils";

export function MessageBubble({
  message,
  visitorLabel,
  agentLabel,
}: {
  message: ChatMessage;
  visitorLabel: string;
  agentLabel: string;
}) {
  const isNote = isInternalNote(message);
  const isVisitor = message.senderType === "VISITOR";
  const isAgent = message.senderType === "AGENT";

  if (isNote) {
    return (
      <article
        aria-label="Internal note — not visible to visitor"
        className="relative overflow-hidden rounded-lg border-2 border-dashed border-warning bg-warning/10 p-4 shadow-inner"
      >
        <div
          className="pointer-events-none absolute inset-x-0 top-0 h-1 bg-[repeating-linear-gradient(45deg,var(--warning),var(--warning)_8px,transparent_8px,transparent_16px)]"
          aria-hidden="true"
        />
        <div className="mb-2 flex items-center gap-2 text-sm font-semibold text-warning">
          <Lock className="h-4 w-4 shrink-0" aria-hidden="true" />
          <span>PRIVATE — Internal note</span>
          <Badge variant="warning" className="text-[10px]">Agents only</Badge>
        </div>
        <p className="whitespace-pre-wrap text-sm">{message.body}</p>
        <RelativeTime date={message.occurredAt} className="mt-2 block text-xs text-text-muted" />
      </article>
    );
  }

  return (
    <article
      className={cn(
        "rounded-lg border border-border p-4",
        isVisitor && "mr-0 bg-surface sm:mr-8",
        isAgent && "ml-0 border-primary/20 bg-primary/5 sm:ml-8",
        message.senderType === "SYSTEM" && "bg-surface-muted/30 text-center text-xs text-text-muted",
      )}
    >
      {message.senderType !== "SYSTEM" && (
        <div className="mb-2 flex items-center gap-2 text-sm">
          <Avatar className="h-6 w-6">
            <AvatarFallback className="text-[10px]">
              {(isVisitor ? visitorLabel : agentLabel).slice(0, 2).toUpperCase()}
            </AvatarFallback>
          </Avatar>
          <span className="font-medium">{isVisitor ? visitorLabel : agentLabel}</span>
          <Badge variant={isAgent ? "default" : "secondary"} className="text-[10px]">
            {isVisitor ? "Visitor" : "Agent reply"}
          </Badge>
          <RelativeTime date={message.occurredAt} className="text-xs" />
        </div>
      )}
      <p className="whitespace-pre-wrap text-sm">{message.body}</p>
    </article>
  );
}
