import DOMPurify from "dompurify";
import { ChevronDown, ChevronUp } from "lucide-react";
import { useMemo, useState } from "react";
import { cn } from "@/lib/utils";

interface SanitizedEmailHtmlProps {
  html: string;
  className?: string;
}

export function SanitizedEmailHtml({ html, className }: SanitizedEmailHtmlProps) {
  const sanitized = useMemo(
    () =>
      DOMPurify.sanitize(html, {
        USE_PROFILES: { html: true },
        ADD_ATTR: ["target"],
        FORBID_TAGS: ["script", "iframe", "object", "embed", "form"],
      }),
    [html],
  );

  return (
    <div
      className={cn("email-html isolate overflow-auto rounded-md border border-border bg-surface p-4", className)}
      dangerouslySetInnerHTML={{ __html: sanitized }}
    />
  );
}

interface CollapsibleQuotedTextProps {
  text: string;
  className?: string;
}

export function CollapsibleQuotedText({ text, className }: CollapsibleQuotedTextProps) {
  const lines = text.split("\n");
  const quoteStart = lines.findIndex((l) => l.startsWith(">"));
  const hasQuote = quoteStart >= 0;
  const [expanded, setExpanded] = useState(false);

  if (!hasQuote) {
    return <pre className={cn("whitespace-pre-wrap font-sans text-sm", className)}>{text}</pre>;
  }

  const main = lines.slice(0, quoteStart).join("\n").trim();
  const quoted = lines.slice(quoteStart).join("\n");

  return (
    <div className={cn("text-sm", className)}>
      <pre className="whitespace-pre-wrap font-sans">{main}</pre>
      <button
        type="button"
        onClick={() => setExpanded(!expanded)}
        className="mt-2 flex items-center gap-1 text-xs text-text-muted hover:text-text"
        aria-expanded={expanded}
      >
        {expanded ? <ChevronUp className="h-3 w-3" /> : <ChevronDown className="h-3 w-3" />}
        {expanded ? "Hide quoted text" : "Show quoted text"}
      </button>
      {expanded && (
        <pre className="mt-2 whitespace-pre-wrap border-l-2 border-border pl-3 font-sans text-text-muted">
          {quoted}
        </pre>
      )}
    </div>
  );
}
