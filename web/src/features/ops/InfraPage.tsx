import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { PageHeader } from "@/components/shared/PageHeader";
import { ErrorState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Metric } from "@/features/ops/Metric";
import {
  fetchAwsSummary,
  fetchEc2Instances,
  fetchEcr,
  fetchElastiCache,
  fetchGithubChecks,
  fetchGithubDeploys,
  fetchGithubPulls,
  fetchMobiRevenue,
  fetchOneOpsRevenue,
  fetchPnL,
  fetchProductHealth,
  fetchRds,
  promoteRelease,
} from "@/features/ops/control-plane-api";
import { PROMOTE_SERVICES } from "@/lib/schemas/control-plane";

export default function InfraPage() {
  const qc = useQueryClient();
  const [service, setService] = useState("backend");
  const [tag, setTag] = useState("");

  const aws = useQuery({ queryKey: ["aws-summary"], queryFn: fetchAwsSummary, retry: 1 });
  const ec2 = useQuery({ queryKey: ["ec2"], queryFn: fetchEc2Instances, retry: 1 });
  const rds = useQuery({ queryKey: ["rds"], queryFn: fetchRds, retry: 1 });
  const cache = useQuery({ queryKey: ["elasticache"], queryFn: fetchElastiCache, retry: 1 });
  const ecr = useQuery({ queryKey: ["ecr"], queryFn: fetchEcr, retry: 1 });
  const health = useQuery({ queryKey: ["product-health"], queryFn: fetchProductHealth, retry: 1 });
  const github = useQuery({ queryKey: ["github-checks"], queryFn: fetchGithubChecks, retry: 1 });
  const pulls = useQuery({ queryKey: ["github-pulls"], queryFn: fetchGithubPulls, retry: 1 });
  const deploys = useQuery({ queryKey: ["github-deploys"], queryFn: fetchGithubDeploys, retry: 1 });
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

  const promote = useMutation({
    mutationFn: () => promoteRelease(service, tag.trim()),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["github-deploys"] }),
  });

  const opsDown = aws.isError && ec2.isError;

  return (
    <div className="space-y-6 p-4 md:p-6">
      <PageHeader
        title="Infra"
        description="AWS cost, compute, data stores and image tags · GitHub checks, PRs and Promote — never called from the browser with AWS keys"
      />

      {opsDown && (
        <ErrorState
          message="Platform AWS/infra APIs unreachable. OPERATOR role is required, and the instance role needs Cost Explorer / EC2 / RDS / ElastiCache / ECR read."
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

      <section className="space-y-3 rounded-lg border border-border p-4">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-text-muted">Promote</h2>
        <p className="text-xs text-text-muted">
          Dispatches Infra <code>deploy.yml</code> with the selected service tag. Returns 503 when
          GITHUB_TOKEN is not configured on the backend.
        </p>
        <div className="flex flex-col gap-2 sm:flex-row">
          <Select value={service} onValueChange={setService}>
            <SelectTrigger className="sm:w-56" aria-label="Service">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {PROMOTE_SERVICES.map((item) => (
                <SelectItem key={item.value} value={item.value}>
                  {item.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Input
            value={tag}
            onChange={(event) => setTag(event.target.value)}
            placeholder="Image tag"
            aria-label="Image tag"
          />
          <Button
            disabled={promote.isPending || tag.trim().length === 0}
            onClick={() => promote.mutate()}
          >
            Promote
          </Button>
        </div>
        {promote.isError && (
          <p className="text-sm text-destructive">
            {promote.error instanceof Error ? promote.error.message : "Promote failed"}
          </p>
        )}
        {promote.isSuccess && (
          <p className="text-sm text-text-muted">
            {promote.data.message}
            {promote.data.htmlUrl ? (
              <>
                {" "}
                <a className="underline" href={promote.data.htmlUrl} target="_blank" rel="noreferrer">
                  Open workflow
                </a>
              </>
            ) : null}
          </p>
        )}
      </section>

      <ListSection title="EC2" loading={ec2.isLoading}>
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
      </ListSection>

      <ListSection title="RDS" loading={rds.isLoading}>
        {(rds.data?.instances ?? []).map((row) => (
          <div key={row.id ?? row.endpoint} className="flex items-center justify-between gap-3 px-4 py-3">
            <div>
              <div className="font-medium">{row.id}</div>
              <div className="text-xs text-text-muted">
                {[row.engine, row.clazz, row.endpoint].filter(Boolean).join(" · ")}
              </div>
            </div>
            <Badge variant="secondary">{row.status ?? "unknown"}</Badge>
          </div>
        ))}
      </ListSection>

      <ListSection title="ElastiCache" loading={cache.isLoading}>
        {(cache.data?.clusters ?? []).map((row) => (
          <div key={row.id} className="flex items-center justify-between gap-3 px-4 py-3">
            <div>
              <div className="font-medium">{row.id}</div>
              <div className="text-xs text-text-muted">
                {[row.engine, row.nodeType].filter(Boolean).join(" · ")}
              </div>
            </div>
            <Badge variant="secondary">{row.status ?? "unknown"}</Badge>
          </div>
        ))}
      </ListSection>

      <ListSection title="ECR (running image tags)" loading={ecr.isLoading}>
        {(ecr.data?.images ?? []).map((row) => (
          <div key={`${row.repository}-${row.digest ?? row.tag}`} className="flex items-center justify-between gap-3 px-4 py-3">
            <div>
              <div className="font-medium">{row.repository}</div>
              <div className="text-xs text-text-muted">
                {[row.tag, row.pushedAt, row.digest].filter(Boolean).join(" · ")}
              </div>
            </div>
            <Badge variant="secondary">{row.tag ?? "untagged"}</Badge>
          </div>
        ))}
      </ListSection>

      <ListSection title="Product health" loading={health.isLoading}>
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
      </ListSection>

      <ListSection title="CI" loading={github.isLoading} note={github.data?.note}>
        {(github.data?.checks ?? []).map((c) => (
          <div key={c.repo} className="flex items-center justify-between gap-3 px-4 py-3">
            <div>
              <div className="font-medium">{c.repo}</div>
              <div className="text-xs text-text-muted">{[c.name, c.status, c.conclusion].filter(Boolean).join(" · ")}</div>
            </div>
            <Badge variant="secondary">{(c.conclusion ?? c.status ?? "unknown").toUpperCase()}</Badge>
          </div>
        ))}
      </ListSection>

      <ListSection title="Open PRs" loading={pulls.isLoading} note={pulls.data?.note ?? undefined}>
        {(pulls.data?.pulls ?? []).map((p) => (
          <div key={`${p.repo}-${p.number}`} className="flex items-center justify-between gap-3 px-4 py-3">
            <div>
              <div className="font-medium">
                {p.repo}#{p.number} {p.title}
              </div>
              <div className="text-xs text-text-muted">{p.user}</div>
            </div>
            {p.htmlUrl ? (
              <a className="text-xs underline" href={p.htmlUrl} target="_blank" rel="noreferrer">
                GitHub
              </a>
            ) : null}
          </div>
        ))}
      </ListSection>

      <ListSection title="Deploy runs" loading={deploys.isLoading} note={deploys.data?.note ?? undefined}>
        {(deploys.data?.runs ?? []).map((run) => (
          <div key={run.id ?? run.htmlUrl} className="flex items-center justify-between gap-3 px-4 py-3">
            <div>
              <div className="font-medium">{run.name}</div>
              <div className="text-xs text-text-muted">
                {[run.status, run.conclusion, run.createdAt, run.headSha?.slice(0, 8)].filter(Boolean).join(" · ")}
              </div>
            </div>
            <Badge variant={run.conclusion === "success" ? "default" : "secondary"}>
              {(run.conclusion ?? run.status ?? "unknown").toUpperCase()}
            </Badge>
          </div>
        ))}
      </ListSection>
    </div>
  );
}

function ListSection({
  title,
  loading,
  note,
  children,
}: {
  title: string;
  loading?: boolean;
  note?: string | null;
  children: React.ReactNode;
}) {
  return (
    <section className="space-y-3">
      <h2 className="text-sm font-semibold uppercase tracking-wide text-text-muted">{title}</h2>
      {note && <p className="text-xs text-text-muted">{note}</p>}
      {loading && <Skeleton className="h-24" />}
      <div className="divide-y rounded-lg border border-border">{children}</div>
    </section>
  );
}
