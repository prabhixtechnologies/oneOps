import { Check, Copy, KeyRound } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Separator } from "@/components/ui/separator";
import { useIssueMailPassword, useRevokeMailPassword } from "@/features/mail/api";
import { getApiErrorMessage } from "@/lib/api-client";
import { mailboxAddress, type MailboxDetail } from "@/lib/schemas/mail";

/** IMAP and SMTP both live on `mail.<domain>` by convention; see mail-server/README.md. */
function serverHost(address: string): string {
  const domain = address.split("@")[1];
  return domain ? `mail.${domain}` : "";
}

function CopyButton({ value, label }: { value: string; label: string }) {
  const [copied, setCopied] = useState(false);

  return (
    <Button
      variant="ghost"
      size="icon"
      aria-label={`Copy ${label}`}
      onClick={() => {
        void navigator.clipboard.writeText(value).then(() => {
          setCopied(true);
          setTimeout(() => setCopied(false), 2000);
        });
      }}
    >
      {copied ? <Check className="h-4 w-4 text-success" /> : <Copy className="h-4 w-4" />}
    </Button>
  );
}

export function MailClientPanel({ mailbox }: { mailbox: MailboxDetail }) {
  const issue = useIssueMailPassword();
  const revoke = useRevokeMailPassword();
  const [issued, setIssued] = useState<string | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [revokeOpen, setRevokeOpen] = useState(false);

  const address = mailboxAddress(mailbox);
  const host = serverHost(address);
  const issuedAt = mailbox.mailPasswordUpdatedAt;

  const generate = () => {
    setConfirmOpen(false);
    void issue
      .mutateAsync(mailbox.id)
      .then((result) => setIssued(result.password))
      .catch((err) => toast.error(getApiErrorMessage(err)));
  };

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <p className="text-sm text-text-muted">
          Connect this mailbox to a desktop or phone mail app over IMAP and SMTP. The password below
          is separate from the sign-in password for the console, so revoking it logs out mail apps
          without touching anyone's account.
        </p>
      </div>

      <div className="rounded-lg border border-border p-4">
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-start gap-3">
            <KeyRound className="mt-0.5 h-5 w-5 text-text-muted" aria-hidden="true" />
            <div>
              <p className="text-sm font-medium">Mail app password</p>
              <p className="text-sm text-text-muted">
                {issuedAt
                  ? `Issued ${new Date(issuedAt).toLocaleDateString(undefined, { dateStyle: "medium" })}`
                  : "Not set — mail apps cannot connect yet"}
              </p>
            </div>
          </div>
          <div className="flex shrink-0 gap-2">
            {issuedAt && (
              <Button variant="outline" size="sm" onClick={() => setRevokeOpen(true)}>
                Revoke
              </Button>
            )}
            <Button
              size="sm"
              disabled={issue.isPending}
              onClick={() => (issuedAt ? setConfirmOpen(true) : generate())}
            >
              {issuedAt ? "Generate new" : "Generate password"}
            </Button>
          </div>
        </div>

        {issued && (
          <>
            <Separator className="my-4" />
            <p className="text-sm font-medium">Your new password</p>
            <p className="mb-2 text-sm text-text-muted">
              Copy it into your mail app now. We store only a hash, so this is the one and only time
              it is shown.
            </p>
            <div className="flex items-center gap-2 rounded-md bg-muted p-2">
              <code className="flex-1 break-all font-mono text-sm">{issued}</code>
              <CopyButton value={issued} label="password" />
            </div>
            <Button variant="ghost" size="sm" className="mt-2" onClick={() => setIssued(null)}>
              I've saved it
            </Button>
          </>
        )}
      </div>

      <div className="rounded-lg border border-border">
        <p className="border-b border-border px-4 py-3 text-sm font-medium">Server settings</p>
        <dl className="divide-y divide-border text-sm">
          {[
            { label: "Username", value: address },
            { label: "IMAP server", value: `${host}:993 (SSL/TLS)` },
            { label: "SMTP server", value: `${host}:587 (STARTTLS)` },
          ].map((row) => (
            <div key={row.label} className="flex items-center justify-between gap-4 px-4 py-3">
              <dt className="text-text-muted">{row.label}</dt>
              <dd className="flex items-center gap-1">
                <span className="font-mono">{row.value}</span>
                <CopyButton value={row.value} label={row.label} />
              </dd>
            </div>
          ))}
        </dl>
      </div>

      <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>Replace the current password?</DialogTitle></DialogHeader>
          <p className="text-sm text-text-muted">
            Every mail app already using {address} will stop syncing until it is given the new
            password.
          </p>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmOpen(false)}>Cancel</Button>
            <Button onClick={generate} disabled={issue.isPending}>Generate new password</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={revokeOpen} onOpenChange={setRevokeOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>Revoke mail app access?</DialogTitle></DialogHeader>
          <p className="text-sm text-text-muted">
            Mail apps will be signed out of {address} immediately. Mail keeps arriving, and the
            console is unaffected.
          </p>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRevokeOpen(false)}>Cancel</Button>
            <Button
              variant="destructive"
              disabled={revoke.isPending}
              onClick={() =>
                void revoke
                  .mutateAsync(mailbox.id)
                  .then(() => {
                    toast.success("Mail app access revoked");
                    setIssued(null);
                    setRevokeOpen(false);
                  })
                  .catch((err) => toast.error(getApiErrorMessage(err)))
              }
            >
              Revoke
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
