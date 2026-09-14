import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { getApiErrorMessage } from "@/lib/api-client";
import { useUpdateMailbox, useIssueMailPassword, useRevokeMailPassword, type BusinessHours, type MailboxDetail } from "@/features/helpdesk/mailbox-admin";
import { useOutletContext } from "react-router";

const WEEKDAYS = [
  { value: 1, label: "Mon" },
  { value: 2, label: "Tue" },
  { value: 3, label: "Wed" },
  { value: 4, label: "Thu" },
  { value: 5, label: "Fri" },
  { value: 6, label: "Sat" },
  { value: 7, label: "Sun" },
];

export function MailboxGeneralSection() {
  const { mailbox } = useOutletContext<{ mailbox: MailboxDetail }>();
  const update = useUpdateMailbox();
  const issuePassword = useIssueMailPassword();
  const revokePassword = useRevokeMailPassword();

  const [name, setName] = useState(mailbox.name);
  const [description, setDescription] = useState(mailbox.description ?? "");
  const [signature, setSignature] = useState(mailbox.signature ?? "");
  const [slaFirst, setSlaFirst] = useState(
    mailbox.slaFirstResponseMins != null ? String(mailbox.slaFirstResponseMins) : "",
  );
  const [slaResolution, setSlaResolution] = useState(
    mailbox.slaResolutionMins != null ? String(mailbox.slaResolutionMins) : "",
  );
  const [timezone, setTimezone] = useState(mailbox.businessHours?.timezone ?? "");
  const [workingDays, setWorkingDays] = useState<number[]>(
    mailbox.businessHours?.workingDays ?? [1, 2, 3, 4, 5],
  );
  const [startTime, setStartTime] = useState(mailbox.businessHours?.startTime ?? "09:00");
  const [endTime, setEndTime] = useState(mailbox.businessHours?.endTime ?? "18:00");
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string>();
  const [issuedPassword, setIssuedPassword] = useState<string>();

  useEffect(() => {
    setName(mailbox.name);
    setDescription(mailbox.description ?? "");
    setSignature(mailbox.signature ?? "");
    setSlaFirst(mailbox.slaFirstResponseMins != null ? String(mailbox.slaFirstResponseMins) : "");
    setSlaResolution(
      mailbox.slaResolutionMins != null ? String(mailbox.slaResolutionMins) : "",
    );
    setTimezone(mailbox.businessHours?.timezone ?? "");
    setWorkingDays(mailbox.businessHours?.workingDays ?? [1, 2, 3, 4, 5]);
    setStartTime(mailbox.businessHours?.startTime ?? "09:00");
    setEndTime(mailbox.businessHours?.endTime ?? "18:00");
  }, [mailbox]);

  const toggleDay = (day: number) => {
    setWorkingDays((prev) =>
      prev.includes(day) ? prev.filter((d) => d !== day) : [...prev, day].sort(),
    );
  };

  const save = async () => {
    setError(undefined);
    setSaved(false);
    const businessHours: BusinessHours = {
      timezone: timezone.trim() || undefined,
      workingDays,
      startTime,
      endTime,
      holidays: mailbox.businessHours?.holidays ?? [],
    };

    // Empty string means leave unchanged; explicit 0 clears the SLA target on the server.
    const parseSla = (raw: string): number | undefined => {
      const trimmed = raw.trim();
      if (trimmed === "") return undefined;
      const n = Number(trimmed);
      return Number.isFinite(n) ? n : undefined;
    };

    try {
      await update.mutateAsync({
        mailboxId: mailbox.id,
        name: name.trim() || undefined,
        description,
        signature,
        slaFirstResponseMins: parseSla(slaFirst),
        slaResolutionMins: parseSla(slaResolution),
        businessHours,
      });
      setSaved(true);
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  return (
    <form
      className="space-y-6"
      onSubmit={(e) => {
        e.preventDefault();
        void save();
      }}
    >
      {error ? (
        <p className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </p>
      ) : null}
      {saved ? <p className="text-sm text-success">Saved.</p> : null}

      <section className="space-y-3">
        <h2 className="text-sm font-medium">Identity</h2>
        <div>
          <label className="mb-1 block text-xs text-text-muted" htmlFor="mb-name-edit">
            Display name
          </label>
          <Input id="mb-name-edit" value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <div>
          <label className="mb-1 block text-xs text-text-muted" htmlFor="mb-desc">
            Description
          </label>
          <Textarea
            id="mb-desc"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={2}
          />
        </div>
        <div>
          <label className="mb-1 block text-xs text-text-muted" htmlFor="mb-sig">
            Signature (HTML)
          </label>
          <Textarea
            id="mb-sig"
            value={signature}
            onChange={(e) => setSignature(e.target.value)}
            rows={3}
          />
        </div>
      </section>

      <section className="space-y-3">
        <h2 className="text-sm font-medium">SLA targets</h2>
        <p className="text-xs text-text-muted">
          Minutes until first response and until resolution. Leave blank to keep unchanged; enter 0 to
          clear a target.
        </p>
        <div className="grid gap-3 sm:grid-cols-2">
          <div>
            <label className="mb-1 block text-xs text-text-muted" htmlFor="sla-first">
              First response (minutes)
            </label>
            <Input
              id="sla-first"
              type="number"
              min={0}
              value={slaFirst}
              onChange={(e) => setSlaFirst(e.target.value)}
              placeholder="e.g. 60"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs text-text-muted" htmlFor="sla-res">
              Resolution (minutes)
            </label>
            <Input
              id="sla-res"
              type="number"
              min={0}
              value={slaResolution}
              onChange={(e) => setSlaResolution(e.target.value)}
              placeholder="e.g. 1440"
            />
          </div>
        </div>
      </section>

      <section className="space-y-3">
        <h2 className="text-sm font-medium">Business hours</h2>
        <div>
          <label className="mb-1 block text-xs text-text-muted" htmlFor="tz">
            Timezone
          </label>
          <Input
            id="tz"
            value={timezone}
            onChange={(e) => setTimezone(e.target.value)}
            placeholder="Europe/London"
          />
        </div>
        <div className="flex flex-wrap gap-1.5">
          {WEEKDAYS.map((d) => (
            <button
              key={d.value}
              type="button"
              onClick={() => toggleDay(d.value)}
              className={`rounded-md border px-2 py-1 text-xs ${
                workingDays.includes(d.value)
                  ? "border-primary bg-primary/15 text-primary"
                  : "border-border text-text-muted"
              }`}
            >
              {d.label}
            </button>
          ))}
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <div>
            <label className="mb-1 block text-xs text-text-muted" htmlFor="bh-start">
              Start
            </label>
            <Input
              id="bh-start"
              type="time"
              value={startTime}
              onChange={(e) => setStartTime(e.target.value)}
            />
          </div>
          <div>
            <label className="mb-1 block text-xs text-text-muted" htmlFor="bh-end">
              End
            </label>
            <Input
              id="bh-end"
              type="time"
              value={endTime}
              onChange={(e) => setEndTime(e.target.value)}
            />
          </div>
        </div>
      </section>

      {mailbox.mailPasswordUpdatedAt ? (
        <p className="text-xs text-text-muted">
          Mail client password last issued{" "}
          {new Date(mailbox.mailPasswordUpdatedAt).toLocaleString()}.
        </p>
      ) : (
        <p className="text-xs text-text-muted">No mail client password has been issued.</p>
      )}

      {issuedPassword ? (
        <p className="rounded-md border border-border bg-surface-muted px-3 py-2 text-sm">
          New IMAP/SMTP password (shown once):{" "}
          <code className="break-all font-mono">{issuedPassword}</code>
        </p>
      ) : null}

      <div className="flex flex-wrap gap-2">
        <Button
          type="button"
          variant="outline"
          disabled={issuePassword.isPending}
          onClick={() => {
            setError(undefined);
            void issuePassword
              .mutateAsync(mailbox.id)
              .then((issued) => setIssuedPassword(issued.password))
              .catch((err) => setError(getApiErrorMessage(err)));
          }}
        >
          {issuePassword.isPending ? "Issuing…" : "Issue mail client password"}
        </Button>
        {mailbox.mailPasswordUpdatedAt ? (
          <Button
            type="button"
            variant="outline"
            disabled={revokePassword.isPending}
            onClick={() => {
              setError(undefined);
              setIssuedPassword(undefined);
              void revokePassword.mutateAsync(mailbox.id).catch((err) => setError(getApiErrorMessage(err)));
            }}
          >
            {revokePassword.isPending ? "Revoking…" : "Revoke password"}
          </Button>
        ) : null}
      </div>

      <Button type="submit" disabled={update.isPending}>
        {update.isPending ? "Saving…" : "Save changes"}
      </Button>
    </form>
  );
}
