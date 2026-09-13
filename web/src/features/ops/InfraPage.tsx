import { useQuery } from "@tanstack/react-query";
import { PageHeader } from "@/components/shared/PageHeader";
import { ErrorState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  fetchAwsSummary,
  fetchEc2Instances,
  fetchGithubChecks,
  fetchMobiRevenue,
  fetchOneOpsRevenue,
  fetchPnL,
  fetchProductHealth,
} from "@/features/ops/control-plane-api";

export default function InfraPage() {
  const aws = useQuery({ queryKey: ["aws-summary"], queryFn: fetchAwsSummary, retry: 1 });
  const ec2 = useQuery({ queryKey: ["ec2"], queryFn: fetchEc2Instances, retry: 1 });
  const health = useQuery({ queryKey: ["product-health"], queryFn: fetchProductHealth, retry: 1 });
  const github = useQuery({ queryKey: ["github-checks"], queryFn: fetchGithubChecks, retry: 1 });
  const mobi = useQuery({ queryKey: ["mobi-revenue"], queryFn: fetchMobiRevenue, retry: 1 });
  const oneops = useQuery({ queryKey: ["oneops-revenue"], queryFn: fetchOneOpsRevenue, retry: 1 });

  const pnl = useQuery({
    queryKey: ["pnl", mobi.data?.capturedTotal, oneops.data?.capturedTotal, aws.data?.costs?.mtdUsd],
    queryFn: () =>
      fetchPnL(
        mobi.data?.capturedTotal ?? 0,
        oneops.data?.capturedTotal ?? 0,
        aws.data?.costs?.mtdUsd,
      ),
    enabled: mobi.isSuccess || oneops.isSuccess || aws.isSuccess,
    retry: 1,
  });

  const opsDown = aws.isError && ec2.isError;

  return (
    <div className="space-y-6 p-4 md:p-6">
      <PageHeader
        title="Infra"
        description="AWS cost & EC2 via Ops Tool · product health · CI — never called from the browser with AWS keys"
      />

      {opsDown && (
        <ErrorState
          message="Platform AWS/infra APIs unreachable. Deploy the platform backend with /admin/platform/aws/* and attach ops-tool-read-policy to prabhix-ec2-ecr-pull."
          onRetry={() => {
            void aws.refetch();
            void ec2.refetch();
          }}
        />
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Metric label="AWS MTD (USD)" value={aws.isLoading ? "…" : `$${(aws.data?.costs?.mtdUsd ?? 0).toFixed(2)}`} />
        <Metric label="EC2 running" value={aws.isLoading ? "…" : String(aws.data?.instances?.running ?? 0)} />
        <Metric
          label="Revenue (Mobi+oneOps ₹)"
          value={
            pnl.isLoading
              ? "…"
              : `₹${(pnl.data?.revenue.total ?? 0).toLocaleString("en-IN")}`
          }
        />
        <Metric
          label="Contribution"
          value={pnl.isLoading ? "…" : String(pnl.data?.contribution?.toFixed(2) ?? "—")}
        />
      </div>
      {pnl.data?.note && <p className="text-xs text-text-muted">{pnl.data.note}</p>}

      <section className="space-y-3">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-text-muted">EC2</h2>
        {ec2.isLoading && <Skeleton className="h-24" />}
        <div className="divide-y rounded-lg border border-border">
          {(ec2.data?.instances ?? []).map((i) => (
            <div key={i.instanceId} className="flex items-center justify-between gap-3 px-4 py-3">
              <div>
                <div className="font-medium">{i.name || i.instanceId}</div>
                <div className="text-xs text-text-muted">
                  {[i.instanceId, i.type, i.az, i.privateIp, i.cpuAverage1h != null ? `CPU ${i.cpuAverage1h}%` : null]
                    .filter(Boolean)
                    .join(" · ")}
                </div>
              </div>
              <Badge variant={i.state === "running" ? "default" : "secondary"}>{i.state}</Badge>
            </div>
          ))}
        </div>
      </section>

      <section className="space-y-3">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-text-muted">Product health</h2>
        {health.isLoading && <Skeleton className="h-24" />}
        <div className="divide-y rounded-lg border border-border">
          {(health.data?.products ?? []).map((p) => (
            <div key={p.name} className="flex items-center justify-between gap-3 px-4 py-3">
              <div>
                <div className="font-medium">{p.name}</div>
                <div className="text-xs text-text-muted">
                  {[p.statusCode != null ? `HTTP ${p.statusCode}` : null, p.latencyMs != null ? `${p.latencyMs}ms` : null, p.error]
                    .filter(Boolean)
                    .join(" · ")}
                </div>
              </div>
              <Badge variant={p.ok ? "default" : "destructive"}>{p.ok ? "OK" : "DOWN"}</Badge>
            </div>
          ))}
        </div>
      </section>

      <section className="space-y-3">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-text-muted">CI</h2>
        {github.data?.note && <p className="text-xs text-text-muted">{github.data.note}</p>}
        <div className="divide-y rounded-lg border border-border">
          {(github.data?.checks ?? []).map((c) => (
            <div key={c.repo} className="flex items-center justify-between gap-3 px-4 py-3">
              <div>
                <div className="font-medium">{c.repo}</div>
                <div className="text-xs text-text-muted">{[c.name, c.status, c.conclusion].filter(Boolean).join(" · ")}</div>
              </div>
              <Badge variant="secondary">{(c.conclusion ?? c.status ?? "unknown").toUpperCase()}</Badge>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-border bg-surface p-4">
      <div className="text-xs uppercase tracking-wide text-text-muted">{label}</div>
      <div className="mt-2 text-2xl font-semibold">{value}</div>
    </div>
  );
}
