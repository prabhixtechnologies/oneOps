import { Activity, AlertTriangle, Clock, Inbox, Users } from "lucide-react";
import { PageHeader } from "@/components/shared/PageHeader";
import { Money } from "@/components/shared/Money";
import { RelativeTime } from "@/components/shared/RelativeTime";
import { Sparkline, MiniBarChart } from "@/components/shared/charts";
import { ErrorState } from "@/components/shared/states";
import { Skeleton } from "@/components/ui/skeleton";
import { useDashboard } from "@/features/mail/api";

function KpiCard({
  label,
  value,
  sub,
  icon: Icon,
  alert,
}: {
  label: string;
  value: React.ReactNode;
  sub?: string;
  icon: React.ComponentType<{ className?: string }>;
  alert?: boolean;
}) {
  return (
    <div className="rounded-lg border border-border bg-surface p-4">
      <div className="flex items-center justify-between">
        <span className="text-sm text-text-muted">{label}</span>
        <Icon className={`h-4 w-4 ${alert ? "text-destructive" : "text-text-muted"}`} aria-hidden="true" />
      </div>
      <div className="mt-2 text-2xl font-semibold">{value}</div>
      {sub && <p className="mt-1 text-xs text-text-muted">{sub}</p>}
    </div>
  );
}

export default function DashboardPage() {
  const { data, isLoading, isError, refetch } = useDashboard();

  if (isLoading) {
    return (
      <div className="space-y-6 p-6">
        <Skeleton className="h-8 w-48" />
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-24" />
          ))}
        </div>
      </div>
    );
  }

  if (isError || !data) {
    return <ErrorState message="Failed to load dashboard" onRetry={() => void refetch()} />;
  }

  const { kpis, recentActivity, threadsTrend, responseTimeTrend } = data;

  return (
    <div className="space-y-6 p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] sm:p-6">
      <PageHeader title="Dashboard" description="Support operations at a glance" />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <KpiCard label="Open threads" value={kpis.openThreads} icon={Inbox} />
        <KpiCard
          label="Avg first response"
          value={`${kpis.avgFirstResponseMinutes}m`}
          icon={Clock}
        />
        <KpiCard
          label="SLA breaches"
          value={kpis.slaBreaches}
          icon={AlertTriangle}
          alert={kpis.slaBreaches > 0}
        />
        <KpiCard
          label="Seats used"
          value={`${kpis.seatsUsed}/${kpis.seatsLimit}`}
          icon={Users}
          sub={`${Math.round((kpis.seatsUsed / kpis.seatsLimit) * 100)}% utilized`}
        />
        <KpiCard
          label="MRR"
          value={<Money amount={kpis.mrr} currency={kpis.currency} />}
          icon={Activity}
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="rounded-lg border border-border bg-surface p-4">
          <h3 className="text-sm font-medium">Open threads (14 days)</h3>
          <div className="mt-4 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
            <Sparkline
              data={threadsTrend.map((p) => p.value)}
              width={280}
              height={48}
              className="h-12 w-full max-w-[280px]"
            />
            <MiniBarChart
              data={threadsTrend.slice(-7).map((p) => ({ label: p.date.slice(5), value: p.value }))}
              className="h-12 w-full max-w-[200px]"
            />
          </div>
        </div>
        <div className="rounded-lg border border-border bg-surface p-4">
          <h3 className="text-sm font-medium">First response time (minutes)</h3>
          <div className="mt-4">
            <Sparkline
              data={responseTimeTrend.map((p) => p.value)}
              width={280}
              height={48}
              color="var(--accent)"
              className="h-12 w-full max-w-[280px]"
            />
          </div>
        </div>
      </div>

      <div className="rounded-lg border border-border bg-surface">
        <div className="border-b border-border px-4 py-3">
          <h3 className="text-sm font-medium">Recent activity</h3>
        </div>
        <ul className="divide-y divide-border">
          {recentActivity.map((item) => (
            <li key={item.id} className="flex flex-col gap-1 px-4 py-3 text-sm sm:flex-row sm:items-center sm:justify-between">
              <div className="min-w-0">
                <p className="break-words">{item.description}</p>
                {item.actor && <p className="text-xs text-text-muted">by {item.actor}</p>}
              </div>
              <RelativeTime date={item.createdAt} className="shrink-0" />
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
