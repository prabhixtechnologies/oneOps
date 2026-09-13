import { Activity, Eye, Inbox, MessageSquare, ShoppingBag, Users } from "lucide-react";
import { PageHeader } from "@/components/shared/PageHeader";
import { Money } from "@/components/shared/Money";
import { RelativeTime } from "@/components/shared/RelativeTime";
import { Sparkline, MiniBarChart } from "@/components/shared/charts";
import { ErrorState } from "@/components/shared/states";
import { Skeleton } from "@/components/ui/skeleton";
import { useDashboard } from "@/features/dashboard/api";

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
    <div className="rounded-xl border border-border bg-surface p-5 shadow-px">
      <div className="flex items-center justify-between">
        <span className="text-sm text-text-muted">{label}</span>
        <Icon className={`h-4 w-4 ${alert ? "text-destructive" : "text-text-muted"}`} aria-hidden="true" />
      </div>
      <div className="mt-2 font-display text-2xl font-semibold">{value}</div>
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
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-24" />
          ))}
        </div>
      </div>
    );
  }

  if (isError || !data) {
    return <ErrorState message="Failed to load dashboard" onRetry={() => void refetch()} />;
  }

  const { kpis, recentActivity, ordersTrend, visitorsTrend } = data;
  const seatUsage = kpis.seatsLimit > 0 ? Math.round((kpis.seatsUsed / kpis.seatsLimit) * 100) : null;

  return (
    <div className="space-y-6 p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] sm:p-6">
      <PageHeader title="Dashboard" description="Your storefront, chat and visitors at a glance" />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <KpiCard label="Open chats" value={kpis.openConversations} icon={MessageSquare} />
        <KpiCard
          label="Waiting for an agent"
          value={kpis.unassignedConversations}
          icon={Inbox}
          alert={kpis.unassignedConversations > 0}
        />
        <KpiCard label="Visitors today" value={kpis.visitorsToday} icon={Eye} />
        <KpiCard
          label="Orders (30 days)"
          value={kpis.ordersLast30Days}
          icon={ShoppingBag}
          sub={`${new Intl.NumberFormat("en-IN", { style: "currency", currency: kpis.currency, minimumFractionDigits: 0 }).format(kpis.revenueLast30Days / 100)} paid`}
        />
        <KpiCard
          label="Seats used"
          value={`${kpis.seatsUsed}/${kpis.seatsLimit}`}
          icon={Users}
          sub={seatUsage === null ? "No seat limit set" : `${seatUsage}% utilized`}
        />
        <KpiCard
          label="MRR"
          value={<Money amount={kpis.mrr} currency={kpis.currency} />}
          icon={Activity}
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="rounded-lg border border-border bg-surface p-4">
          <h2 className="text-sm font-medium">Paid orders (14 days)</h2>
          <div className="mt-4 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
            <Sparkline
              data={ordersTrend.map((p) => p.value)}
              width={280}
              height={48}
              className="h-12 w-full max-w-[280px]"
            />
            <MiniBarChart
              data={ordersTrend.slice(-7).map((p) => ({ label: p.date.slice(5), value: p.value }))}
              className="h-12 w-full max-w-[200px]"
            />
          </div>
        </div>
        <div className="rounded-lg border border-border bg-surface p-4">
          <h2 className="text-sm font-medium">Visitor sessions (14 days)</h2>
          <div className="mt-4">
            <Sparkline
              data={visitorsTrend.map((p) => p.value)}
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
          <h2 className="text-sm font-medium">Recent activity</h2>
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
