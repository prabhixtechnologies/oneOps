import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { PageHeader } from "@/components/shared/PageHeader";
import { ErrorState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Metric } from "@/features/ops/Metric";
import {
  fetchMobiPayments,
  fetchOneOpsRevenue,
} from "@/features/ops/control-plane-api";

export const PAYMENT_FILTERS = [
  { id: "CAPTURED", label: "Captured" },
  { id: "OPEN", label: "Open" },
  { id: "FAILED", label: "Failed" },
  { id: "EXPIRED", label: "Expired" },
  { id: "REFUNDED", label: "Refunded" },
  { id: "ALL", label: "All" },
] as const;

export type PaymentFilter = (typeof PAYMENT_FILTERS)[number]["id"];

const OPEN_STATUSES = new Set(["CREATED", "PENDING", "AUTHORIZED"]);
const REFUNDED_STATUSES = new Set(["REFUNDED", "PARTIALLY_REFUNDED"]);

export function paymentMatches(status: string, filter: PaymentFilter): boolean {
  switch (filter) {
    case "ALL":
      return true;
    case "OPEN":
      return OPEN_STATUSES.has(status);
    case "REFUNDED":
      return REFUNDED_STATUSES.has(status);
    default:
      return status === filter;
  }
}

const inr = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 0,
});

const pct = new Intl.NumberFormat("en-IN", {
  style: "percent",
  maximumFractionDigits: 1,
});

export default function RevenuePage() {
  const revenue = useQuery({
    queryKey: ["oneops-revenue"],
    queryFn: fetchOneOpsRevenue,
    retry: 1,
  });
  const payments = useQuery({ queryKey: ["mobi-payments"], queryFn: fetchMobiPayments, retry: 1 });
  const [paymentFilter, setPaymentFilter] = useState<PaymentFilter>("CAPTURED");
  const rows = payments.data ?? [];
  const visible = useMemo(
    () => rows.filter((p) => paymentMatches(p.status, paymentFilter)),
    [rows, paymentFilter],
  );
  const counts = useMemo(() => {
    const tally = new Map<PaymentFilter, number>();
    for (const filter of PAYMENT_FILTERS) {
      tally.set(filter.id, rows.filter((p) => paymentMatches(p.status, filter.id)).length);
    }
    return tally;
  }, [rows]);

  return (
    <div className="space-y-6 p-4 md:p-6">
      <PageHeader
        title="Revenue"
        description="Active subscriptions, MRR and churn per product — amounts in INR"
      />

      {revenue.isError && (
        <ErrorState
          message="Failed to load revenue. BILLING role is required."
          onRetry={() => void revenue.refetch()}
        />
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Metric
          label="oneOps captured"
          value={revenue.isLoading ? "…" : inr.format(revenue.data?.capturedTotal ?? 0)}
        />
        <Metric
          label="oneOps MRR"
          value={revenue.isLoading ? "…" : inr.format(revenue.data?.mrr ?? 0)}
        />
        <Metric
          label="Active subscriptions"
          value={revenue.isLoading ? "…" : String(revenue.data?.activeSubscriptions ?? 0)}
        />
        <Metric
          label="Churn (30d)"
          value={
            revenue.isLoading
              ? "…"
              : pct.format(revenue.data?.churnRate ?? 0)
          }
        />
      </div>

      <section className="space-y-3">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-text-muted">
          Per product
        </h2>
        {revenue.isLoading && <Skeleton className="h-24" />}
        <div className="divide-y rounded-lg border border-border">
          {(revenue.data?.products ?? []).map((product) => (
            <div key={product.product} className="flex items-center justify-between gap-3 px-4 py-3">
              <div>
                <div className="font-medium">{product.product}</div>
                <div className="text-xs text-text-muted">
                  {[
                    `${product.activeSubscriptions} active`,
                    `churn ${pct.format(product.churnRate)}`,
                    product.note,
                  ]
                    .filter(Boolean)
                    .join(" · ")}
                </div>
              </div>
              <div className="text-right">
                <div className="font-medium">{inr.format(product.capturedTotal)}</div>
                <div className="text-xs text-text-muted">MRR {inr.format(product.mrr)}</div>
              </div>
            </div>
          ))}
        </div>
      </section>

      <section className="space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-text-muted">
            MobiStack payments
          </h2>
          <div className="flex flex-wrap gap-2" role="group" aria-label="Payment status">
            {PAYMENT_FILTERS.map((filter) => (
              <Button
                key={filter.id}
                type="button"
                size="sm"
                variant={paymentFilter === filter.id ? "default" : "outline"}
                aria-pressed={paymentFilter === filter.id}
                onClick={() => setPaymentFilter(filter.id)}
              >
                {filter.label}
                {payments.data ? ` (${counts.get(filter.id) ?? 0})` : ""}
              </Button>
            ))}
          </div>
        </div>
        {payments.isLoading && <Skeleton className="h-24" />}
        {payments.isError && (
          <ErrorState message="Failed to load payments" onRetry={() => void payments.refetch()} />
        )}
        {!payments.isLoading && !payments.isError && visible.length === 0 && (
          <p className="text-sm text-text-muted">
            {paymentFilter === "ALL"
              ? "No payments."
              : `No ${PAYMENT_FILTERS.find((f) => f.id === paymentFilter)?.label.toLowerCase()} payments.`}
          </p>
        )}
        {visible.length > 0 && (
          <div className="divide-y rounded-lg border border-border">
            {visible.map((p) => (
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
        )}
      </section>
    </div>
  );
}
