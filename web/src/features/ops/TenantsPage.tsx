import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { PageHeader } from "@/components/shared/PageHeader";
import { ErrorState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Metric } from "@/features/ops/Metric";
import { TenantsTab } from "@/features/ops/TenantsTab";
import {
  fetchMobiWorkspaces,
  setMobiWorkspaceActive,
} from "@/features/ops/control-plane-api";

export default function TenantsPage() {
  const qc = useQueryClient();
  const shops = useQuery({ queryKey: ["mobi-shops"], queryFn: fetchMobiWorkspaces, retry: 1 });

  const toggle = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      setMobiWorkspaceActive(id, active),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["mobi-shops"] }),
  });

  return (
    <div className="space-y-6 p-4 md:p-6">
      <PageHeader
        title="Tenants and shops"
        description="oneOps organizations and MobiStack shops — suspend a shop without signing in as its owner"
      />

      <section className="space-y-3">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-text-muted">
          Organizations
        </h2>
        <TenantsTab />
      </section>

      <section className="space-y-3">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-text-muted">Shops</h2>
        <Metric
          label="Active shops"
          value={
            shops.isLoading
              ? "…"
              : String((shops.data ?? []).filter((s) => s.active).length)
          }
        />
        {shops.isLoading && <Skeleton className="h-24" />}
        {shops.isError && (
          <ErrorState
            message="Failed to load shops. SUPPORT role is required."
            onRetry={() => void shops.refetch()}
          />
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
    </div>
  );
}
