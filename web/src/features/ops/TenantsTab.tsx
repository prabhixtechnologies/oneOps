import { useState } from "react";
import { ExternalLink } from "lucide-react";
import { CursorList } from "@/components/shared/CursorList";
import { MobileCard, MobileCardRow } from "@/components/shared/ResponsiveTable";
import { RelativeTime } from "@/components/shared/RelativeTime";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/features/ops/OpsHubPage";
import { StatusFilter } from "@/features/ops/StatusFilter";
import { useTenants } from "@/features/ops/api";
import { useAuth } from "@/lib/auth";
import { staffHandoffUrl } from "@/lib/staff-handoff";
import { TENANT_STATUSES, type TenantSummary } from "@/lib/schemas/ops";

export function TenantsTab() {
  const [status, setStatus] = useState<string | undefined>();
  const query = useTenants(status);
  const tenants = query.data?.pages.flatMap((page) => page.items) ?? [];
  const { me } = useAuth();
  const canView = me?.platformAdmin === true;

  /**
   * Opens the customer's workspace in OneOps, in a new tab.
   *
   * <p>A new tab rather than this one, because the two are different jobs: the directory is where
   * staff are working, and a support look-up should not cost them their place in it. It also keeps
   * the two contexts visibly apart — the platform in one tab, one customer in another.
   */
  const view = (tenant: TenantSummary) => {
    window.open(staffHandoffUrl(tenant.id), "_blank", "noopener,noreferrer");
  };

  return (
    <div className="space-y-4">
      <StatusFilter
        label="All statuses"
        statuses={TENANT_STATUSES}
        value={status}
        onChange={setStatus}
      />

      <div className="h-[min(600px,70vh)] rounded-lg border border-border">
        <CursorList<TenantSummary>
          items={tenants}
          hasMore={!!query.hasNextPage}
          isLoading={query.isLoading}
          isError={query.isError}
          errorMessage="Failed to load tenants"
          isFetchingNextPage={query.isFetchingNextPage}
          onLoadMore={() => void query.fetchNextPage()}
          getKey={(tenant) => tenant.id}
          emptyTitle="No tenants"
          emptyDescription="No organization matches this filter."
          estimateSize={64}
          renderItem={(tenant) => (
            <>
              <div className="hidden items-center gap-4 border-b border-border px-4 py-3 text-sm md:flex">
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium">{tenant.name}</p>
                  <p className="truncate font-mono text-xs text-text-muted">{tenant.slug}</p>
                </div>
                <StatusBadge status={tenant.status} />
                <span className="w-24 shrink-0 text-right text-text-muted">
                  {tenant.memberCount}/{tenant.seatLimit} seats
                </span>
                <RelativeTime date={tenant.createdAt} className="w-28 shrink-0 text-right text-xs" />
                {canView && (
                  <Button
                    size="sm"
                    variant="ghost"
                    className="w-24 shrink-0"
                    onClick={() => view(tenant)}
                    title={`Open ${tenant.name} in OneOps. This access is recorded.`}
                  >
                    <ExternalLink className="mr-1 h-3.5 w-3.5" aria-hidden="true" />
                    Open
                  </Button>
                )}
              </div>
              <div className="p-2 md:hidden">
                <MobileCard>
                  <div className="flex items-center justify-between gap-2">
                    <p className="min-w-0 truncate font-medium">{tenant.name}</p>
                    <StatusBadge status={tenant.status} />
                  </div>
                  <MobileCardRow label="Slug" value={tenant.slug} />
                  <MobileCardRow
                    label="Seats"
                    value={`${tenant.memberCount}/${tenant.seatLimit}`}
                  />
                  <MobileCardRow label="Created" value={<RelativeTime date={tenant.createdAt} />} />
                  {tenant.trialEndsAt && (
                    <MobileCardRow
                      label="Trial ends"
                      value={<RelativeTime date={tenant.trialEndsAt} />}
                    />
                  )}
                  {canView && (
                    <Button
                      size="sm"
                      variant="outline"
                      className="mt-2 w-full"
                      onClick={() => view(tenant)}
                    >
                      <ExternalLink className="mr-1 h-3.5 w-3.5" aria-hidden="true" />
                      Open in OneOps
                    </Button>
                  )}
                </MobileCard>
              </div>
            </>
          )}
        />
      </div>
    </div>
  );
}
