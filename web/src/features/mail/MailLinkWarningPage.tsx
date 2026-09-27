import { useMemo, useState } from "react";
import { useSearchParams } from "react-router";
import { Button } from "@/components/ui/button";
import { parseMailLinkTarget } from "@/features/mail/mailLinkWarning";

export function MailLinkWarningPage() {
  const [params] = useSearchParams();
  const parsed = useMemo(() => parseMailLinkTarget(params.get("target")), [params]);
  const [confirmed, setConfirmed] = useState(false);

  if (parsed.kind === "invalid") {
    return (
      <div className="mx-auto max-w-lg space-y-4 p-8 text-center">
        <h1 className="text-xl font-semibold">Link could not be opened</h1>
        <p className="text-muted-foreground text-sm">
          This tracking link does not point to a safe https destination. You can close this tab.
        </p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-lg space-y-6 p-8">
      <div className="space-y-2 text-center">
        <h1 className="text-xl font-semibold">You are leaving Prabhix mail</h1>
        <p className="text-muted-foreground text-sm">
          The message linked to an external site. We only show the destination host here — not the full URL —
          so you can decide before continuing.
        </p>
      </div>
      <div className="rounded-md border bg-muted/40 px-4 py-3 text-center font-mono text-sm">{parsed.host}</div>
      {!confirmed ? (
        <Button className="w-full" type="button" onClick={() => setConfirmed(true)}>
          Continue to this site
        </Button>
      ) : (
        <Button className="w-full" type="button" asChild>
          <a href={parsed.href} rel="noopener noreferrer">
            Open https://{parsed.host}
          </a>
        </Button>
      )}
    </div>
  );
}
