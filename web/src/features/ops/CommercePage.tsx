import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { PageHeader } from "@/components/shared/PageHeader";
import { ErrorState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  fetchMobiPayments,
  fetchMobiRevenue,
  fetchMobiWorkspaces,
  fetchOneOpsRevenue,
  setMobiWorkspaceActive,
} from "@/features/ops/control-plane-api";

const inr = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 0,
});

export default function CommercePage() {
  const qc = useQueryClient();
  const mobiRev = useQuery({ queryKey: ["mobi-revenue"], queryFn: fetchMobiRevenue, retry: 1 });
  const oneopsRev = useQuery({
    queryKey: ["oneops-revenue"],
    queryFn: fetchOneOpsRevenue,
    retry: 1,
  });
  const payments = useQuery({ queryKey: ["mobi-payments"], queryFn: fetchMobiPayments, retry: 1 });
  const shops = useQuery({ queryKey: ["mobi-shops"], queryFn: fetchMobiWorkspaces, retry: 1 });

  const toggle = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      setMobiWorkspaceActive(id, active),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["mobi-shops"] }),
  });

  const authHint =
    mobiRev.error?.message.includes("403") || shops.error?.message.includes("403")
      ? "MobiStack system_admin required — run deploy/promote-system-admin.sql for your email."
      : null;

  return (
    <div className="space-y-6 p-4 md:p-6">
      <PageHeader
        title="Commerce"
        description="MobiStack payments and shops · oneOps SaaS revenue"
      />

      {authHint && <p className="text-sm text-destructive">{authHint}</p>}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Metric
          label="MobiStack received"
          value={mobiRev.isLoading ? "…" : inr.format(mobiRev.data?.capturedTotal ?? 0)}
        />
        <Metric
          label="MobiStack pending"
          value={mobiRev.isLoading ? "…" : inr.format(mobiRev.data?.pendingTotal ?? 0)}
        />
        <Metric
          label="oneOps SaaS received"
          value={oneopsRev.isLoading ? "…" : inr.format(oneopsRev.data?.capturedTotal ?? 0)}
        />
        <Metric
          label="Active shops"
          value={
            shops.isLoading
              ? "…"
              : String((shops.data ?? []).filter((s) => s.active).length)
          }
        />
      </div>

      <section className="space-y-3">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-text-muted">Shops</h2>
        {shops.isLoading && <Skeleton className="h-24" />}
        {shops.isError && (
          <ErrorState message="Failed to load shops" onRetry={() => void shops.refetch()} />
        )}
        <div className="divide-y rounded-lg border border-border">
          {(shops.data ?? []).map((shop) => (
            <div key={shop.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div>
                <div className="font-medium">{shop.name}</div>
                <div className="text-xs text-text-muted">
                  {[shop.city, `${shop.members ?? 0} members`].filter(Boolean).join(" · ")}
                </div>
              </div>
              <div className="flex items-center gap-2">
                <Badge variant={shop.active ? "default" : "secondary"}>
                  {shop.active ? "ACTIVE" : "SUSPENDED"}
                </Badge>
                <Button
                  size="sm"
                  variant="outline"
                  disabled={toggle.isPending}
                  onClick={() => toggle.mutate({ id: shop.id, active: !shop.active })}
                >
                  {shop.active ? "Suspend" : "Activate"}
                </Button>
              </div>
            </div>
          ))}
        </div>
      </section>

      <section className="space-y-3">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-text-muted">Payments</h2>
        {payments.isLoading && <Skeleton className="h-24" />}
        {payments.isError && (
          <ErrorState message="Failed to load payments" onRetry={() => void payments.refetch()} />
        )}
        <div className="divide-y rounded-lg border border-border">
          {(payments.data ?? []).slice(0, 50).map((p) => (
            <div key={p.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div>
                <div className="font-medium">{p.shopName ?? "Shop"}</div>
                <div className="text-xs text-text-muted">
                  {[p.priceCode, p.paidAt ?? p.createdAt].filter(Boolean).join(" · ")}
                </div>
              </div>
              <div className="flex items-center gap-2">
                <span className="font-medium">{inr.format(p.amount)}</span>
                <Badge variant={p.status === "CAPTURED" ? "default" : "secondary"}>{p.status}</Badge>
              </div>
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
