import { Link } from "react-router";
import { PageHeader } from "@/components/shared/PageHeader";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { Money } from "@/components/shared/Money";
import { ErrorState } from "@/components/shared/states";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useCommerceDashboard } from "@/features/commerce/api";
import { PERMISSIONS } from "@/lib/permissions";

export default function CommerceDashboardPage() {
  const { data, isLoading, isError, refetch } = useCommerceDashboard();

  return (
    <div className="space-y-6 p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] md:p-6">
      <PageHeader
        title="Shop dashboard"
        description="Revenue, orders, and top products from the last 30 days."
        actions={
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" asChild>
              <Link to="/commerce/products">Products</Link>
            </Button>
            <Button variant="outline" asChild>
              <Link to="/commerce/orders">Orders</Link>
            </Button>
          </div>
        }
      />

      <PermissionGate
        permission={PERMISSIONS.COMMERCE_ORDER_READ}
        fallback={<p className="text-sm text-text-muted">You do not have permission to view commerce analytics.</p>}
      >
        {isLoading && (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            <Skeleton className="h-24" />
            <Skeleton className="h-24" />
            <Skeleton className="h-24" />
          </div>
        )}
        {isError && (
          <ErrorState message="Failed to load dashboard" onRetry={() => void refetch()} />
        )}
        {data && (
          <div className="space-y-6">
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              <div className="rounded-lg border border-border p-4">
                <p className="text-xs uppercase text-text-muted">Revenue (30d)</p>
                <p className="text-2xl font-semibold">
                  <Money amount={data.revenuePaise30d} />
                </p>
              </div>
              <div className="rounded-lg border border-border p-4">
                <p className="text-xs uppercase text-text-muted">Orders (30d)</p>
                <p className="text-2xl font-semibold">{data.orderCount30d}</p>
              </div>
              <div className="rounded-lg border border-border p-4">
                <p className="text-xs uppercase text-text-muted">Conversion rate</p>
                <p className="text-2xl font-semibold">{(data.conversionRate * 100).toFixed(1)}%</p>
              </div>
            </div>
            {data.topProducts.length > 0 && (
              <div>
                <h2 className="mb-2 font-medium">Top products</h2>
                <ul className="divide-y divide-border rounded-lg border border-border">
                  {data.topProducts.map((p) => (
                    <li key={p.productId} className="flex justify-between px-4 py-2 text-sm">
                      <span>{p.productName}</span>
                      <span className="text-text-muted">{p.quantitySold} sold</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}
      </PermissionGate>
    </div>
  );
}
