import { useState } from "react";
import {
  AlertTriangle,
  Building2,
  ExternalLink,
  Mail,
  RefreshCw,
  ShieldAlert,
  Users,
} from "lucide-react";
import { PageHeader } from "@/components/shared/PageHeader";
import { RelativeTime } from "@/components/shared/RelativeTime";
import { ErrorState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { usePlatformOverview } from "@/features/ops/api";
import { ApplicationsTab, LeadsTab, SubscribersTab } from "@/features/ops/SitePipeline";
import { TenantsTab } from "@/features/ops/TenantsTab";
import { useQuery } from "@tanstack/react-query";
import {
  fetchAwsSummary,
  fetchMobiRevenue,
  fetchOneOpsRevenue,
} from "@/features/ops/control-plane-api";
import { useAuth } from "@/lib/auth";
import { staffHandoffUrl } from "@/lib/staff-handoff";
import { cn } from "@/lib/utils";
import { Link } from "react-router";

/**
 * Where Prabhix's own work happens, which is not here.
 *
 * <p>The company is a customer of its own product: its inbox, chat and shop are OneOps pointed at
 * the Prabhix organization, reached with the same sign-in. Saying so on the page staff open every
 * day is cheaper than explaining repeatedly why this console has no inbox.
 */
function OwnWorkspaceLink() {
  const { organization } = useAuth();

  return (
    <Button size="sm" variant="secondary" asChild>
      <a href={staffHandoffUrl(organization?.id ?? "", "/")} target="_blank" rel="noopener noreferrer">
        <Building2 className="mr-1 h-3.5 w-3.5" aria-hidden="true" />
        Open {organization?.name ?? "your workspace"}
        <ExternalLink className="ml-1 h-3 w-3" aria-hidden="true" />
      </a>
    </Button>
  );
}

export default function OpsHubPage() {
  const [tab, setTab] = useState("overview");

  return (
    <div className="space-y-6 p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] md:p-6">
      <PageHeader
        title="Ops Hub"
        description="Platform-wide operations across every tenant — counts, backlogs and the marketing pipeline"
        actions={<OwnWorkspaceLink />}
      />

      <Tabs value={tab} onValueChange={setTab}>
        <TabsList className="w-full justify-start overflow-x-auto">
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="tenants">Tenants</TabsTrigger>
          <TabsTrigger value="leads">Leads</TabsTrigger>
          <TabsTrigger value="subscribers">Subscribers</TabsTrigger>
          <TabsTrigger value="applications">Applications</TabsTrigger>
        </TabsList>

        <TabsContent value="overview" className="mt-6">
          <OverviewTab />
        </TabsContent>
        <TabsContent value="tenants" className="mt-6">
          <TenantsTab />
        </TabsContent>
        <TabsContent value="leads" className="mt-6">
          <LeadsTab />
        </TabsContent>
        <TabsContent value="subscribers" className="mt-6">
          <SubscribersTab />
        </TabsContent>
        <TabsContent value="applications" className="mt-6">
          <ApplicationsTab />
        </TabsContent>
      </Tabs>
    </div>
  );
}

function OverviewTab() {
  const { data, isLoading, isError, refetch, isFetching } = usePlatformOverview();
  const mobi = useQuery({ queryKey: ["mobi-revenue"], queryFn: fetchMobiRevenue, retry: 1 });
  const oneops = useQuery({ queryKey: ["oneops-revenue"], queryFn: fetchOneOpsRevenue, retry: 1 });
  const aws = useQuery({ queryKey: ["aws-summary"], queryFn: fetchAwsSummary, retry: 1 });

  if (isLoading) {
    return (
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {Array.from({ length: 8 }).map((_, i) => (
          <Skeleton key={i} className="h-24" />
        ))}
      </div>
    );
  }

  if (isError || !data) {
    return <ErrorState message="Failed to load the platform overview" onRetry={() => void refetch()} />;
  }

  const { tenants, accounts, queues, activity } = data;
  const inr = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-3">
        <p className="text-xs text-text-muted">
          Measured <RelativeTime date={data.generatedAt} />
        </p>
        <Button
          variant="outline"
          size="sm"
          className="gap-2"
          onClick={() => void refetch()}
          disabled={isFetching}
        >
          <RefreshCw className={cn("h-4 w-4", isFetching && "animate-spin")} />
          Refresh
        </Button>
      </div>

      <section>
        <div className="mb-3 flex items-center justify-between gap-2">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-text-muted">
            Commerce & infra
          </h2>
          <div className="flex gap-2 text-xs">
            <Link className="text-accent underline-offset-2 hover:underline" to="/commerce">
              Commerce
            </Link>
            <Link className="text-accent underline-offset-2 hover:underline" to="/infra">
              Infra
            </Link>
          </div>
        </div>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard
            label="MobiStack received"
            value={mobi.isError ? "—" : inr.format(mobi.data?.capturedTotal ?? 0)}
            sub={mobi.isError ? "system_admin / API" : `${mobi.data?.capturedCount ?? 0} captured`}
            icon={Users}
          />
          <StatCard
            label="oneOps SaaS"
            value={oneops.isError ? "—" : inr.format(oneops.data?.capturedTotal ?? 0)}
            sub={`${oneops.data?.capturedCount ?? 0} captured`}
            icon={Users}
          />
          <StatCard
            label="AWS MTD"
            value={aws.isError ? "—" : `$${(aws.data?.costs?.mtdUsd ?? 0).toFixed(2)}`}
            sub="Cost Explorer via Ops Tool"
            icon={ShieldAlert}
          />
          <StatCard
            label="EC2 running"
            value={aws.isError ? "—" : String(aws.data?.instances?.running ?? 0)}
            sub={`of ${aws.data?.instances?.total ?? 0} instances`}
            icon={AlertTriangle}
          />
        </div>
      </section>

      <section>
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-text-muted">
          Needs attention
        </h2>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard
            label="Mail failed"
            value={queues.mailFailed}
            sub="Attempts exhausted — needs a human"
            icon={Mail}
            alert={queues.mailFailed > 0}
          />
          <StatCard
            label="Mail queued"
            value={queues.mailPending}
            sub="Waiting on a transport"
            icon={Mail}
            alert={queues.mailPending > 50}
          />
          <StatCard
            label="Errors (24h)"
            value={activity.errorsLast24h}
            sub="ERROR and FATAL events"
            icon={AlertTriangle}
            alert={activity.errorsLast24h > 0}
          />
          <StatCard
            label="Security events (24h)"
            value={activity.securityEventsLast24h}
            sub="Sign-in failures, revocations, reuse"
            icon={ShieldAlert}
            alert={activity.securityEventsLast24h > 0}
          />
        </div>
      </section>

      <section>
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-text-muted">
          Tenants
        </h2>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard label="Total" value={tenants.total} icon={Building2} />
          <StatCard label="Active" value={tenants.active} icon={Building2} />
          <StatCard label="On trial" value={tenants.trial} icon={Building2} />
          <StatCard
            label="Suspended"
            value={tenants.suspended}
            icon={Building2}
            alert={tenants.suspended > 0}
          />
        </div>
        <p className="mt-3 text-xs text-text-muted">
          {tenants.createdLast30Days} created in the last 30 days · {tenants.cancelled} cancelled
        </p>
      </section>

      <section>
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-text-muted">
          Accounts
        </h2>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard label="Total" value={accounts.total} icon={Users} />
          <StatCard label="Active" value={accounts.active} icon={Users} />
          <StatCard
            label="Locked out"
            value={accounts.lockedOut}
            sub="Too many failed sign-ins"
            icon={Users}
            alert={accounts.lockedOut > 0}
          />
          <StatCard label="Live sessions" value={queues.activeSessions} icon={Users} />
        </div>
        <p className="mt-3 text-xs text-text-muted">
          {accounts.platformAdmins} platform admin{accounts.platformAdmins === 1 ? "" : "s"} ·{" "}
          {accounts.invited} invited · {accounts.disabled} disabled ·{" "}
          {accounts.createdLast30Days} joined in the last 30 days
        </p>
      </section>
    </div>
  );
}

function StatCard({
  label,
  value,
  sub,
  icon: Icon,
  alert,
}: {
  label: string;
  value: number;
  sub?: string;
  icon: React.ComponentType<{ className?: string }>;
  alert?: boolean;
}) {
  return (
    <div
      className={cn(
        "rounded-lg border bg-surface p-4",
        alert ? "border-destructive/40" : "border-border",
      )}
    >
      <div className="flex items-center justify-between">
        <span className="text-sm text-text-muted">{label}</span>
        <Icon
          className={cn("h-4 w-4", alert ? "text-destructive" : "text-text-muted")}
          aria-hidden="true"
        />
      </div>
      <div className={cn("mt-2 text-2xl font-semibold", alert && "text-destructive")}>
        {value.toLocaleString()}
      </div>
      {sub && <p className="mt-1 text-xs text-text-muted">{sub}</p>}
    </div>
  );
}

/** Shared by the tenant directory and the three pipeline lists. */
export function StatusBadge({ status }: { status: string }) {
  return (
    <Badge variant="outline" className={statusClass(status)}>
      {status.replace(/_/g, " ")}
    </Badge>
  );
}

function statusClass(status: string) {
  switch (status) {
    case "ACTIVE":
    case "CONFIRMED":
    case "WON":
    case "HIRED":
      return "border-emerald-500/30 bg-emerald-500/15 text-emerald-700 dark:text-emerald-400";
    case "SUSPENDED":
    case "BOUNCED":
    case "LOST":
    case "SPAM":
    case "REJECTED":
      return "border-destructive/30 bg-destructive/15 text-destructive";
    case "TRIAL":
    case "PENDING":
    case "NEW":
    case "RECEIVED":
      return "border-primary/20 bg-primary/10 text-primary";
    case "CANCELLED":
    case "UNSUBSCRIBED":
    case "WITHDRAWN":
      return "border-border bg-muted text-text-muted";
    default:
      return "border-amber-500/30 bg-amber-500/15 text-amber-700 dark:text-amber-400";
  }
}
