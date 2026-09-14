import { useQuery } from "@tanstack/react-query";
import { PageHeader } from "@/components/shared/PageHeader";
import { ErrorState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Metric } from "@/features/ops/Metric";
import { fetchMailHealth } from "@/features/ops/control-plane-api";

export default function MailHealthPage() {
  const health = useQuery({ queryKey: ["mail-health"], queryFn: fetchMailHealth, retry: 1 });

  return (
    <div className="space-y-6 p-4 md:p-6">
      <PageHeader
        title="Mail health"
        description="SES quota, outbox depth, domain verification and mailbox counts"
      />

      {health.isError && (
        <ErrorState
          message="Failed to load mail health. SUPPORT role is required."
          onRetry={() => void health.refetch()}
        />
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Metric
          label="Outbox in flight"
          value={health.isLoading ? "…" : String(health.data?.outbox?.inFlight ?? 0)}
        />
        <Metric
          label="Outbox failed"
          value={health.isLoading ? "…" : String(health.data?.outbox?.failed ?? 0)}
        />
        <Metric
          label="Mailboxes"
          value={health.isLoading ? "…" : String(health.data?.mailboxes ?? 0)}
        />
        <Metric
          label="Domains verified"
          value={
            health.isLoading
              ? "…"
              : `${health.data?.domains?.verified ?? 0} / ${health.data?.domains?.total ?? 0}`
          }
        />
      </div>

      <section className="space-y-3 rounded-lg border border-border p-4">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-text-muted">SES</h2>
          <Badge variant={health.data?.ses?.ok ? "default" : "secondary"}>
            {health.data?.ses?.ok ? "OK" : "CHECK"}
          </Badge>
        </div>
        <p className="text-sm text-text-muted">{health.data?.ses?.note ?? "—"}</p>
        <div className="grid gap-4 sm:grid-cols-3">
          <Metric label="Max 24h" value={String(health.data?.ses?.max24HourSend ?? "—")} />
          <Metric label="Max send rate" value={String(health.data?.ses?.maxSendRate ?? "—")} />
          <Metric label="Sent last 24h" value={String(health.data?.ses?.sentLast24Hours ?? "—")} />
        </div>
      </section>

      <div className="grid gap-4 sm:grid-cols-2">
        <Metric
          label="Bounces (suppressed)"
          value={health.isLoading ? "…" : String(health.data?.suppressions?.bounces ?? 0)}
        />
        <Metric
          label="Complaints"
          value={health.isLoading ? "…" : String(health.data?.suppressions?.complaints ?? 0)}
        />
      </div>
    </div>
  );
}
