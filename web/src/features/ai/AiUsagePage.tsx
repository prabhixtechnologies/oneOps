import { useMemo } from "react";
import { Link } from "react-router";
import { PageHeader } from "@/components/shared/PageHeader";
import { Money } from "@/components/shared/Money";
import { RelativeTime } from "@/components/shared/RelativeTime";
import { ErrorState, EmptyState } from "@/components/shared/states";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { MobileCard, MobileCardRow, ResponsiveTable } from "@/components/shared/ResponsiveTable";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { PERMISSIONS } from "@/lib/permissions";
import { useAiUsage, useAiUsageSummary } from "@/features/ai/api";

export default function AiUsagePage() {
  const summaryQuery = useAiUsageSummary();
  const usageQuery = useAiUsage();

  const rows = useMemo(
    () => usageQuery.data?.pages.flatMap((p) => p.items) ?? [],
    [usageQuery.data],
  );

  return (
    <PermissionGate
      permission={PERMISSIONS.AI_USAGE_READ}
      fallback={
        <div className="p-6">
          <PageHeader title="AI usage" description="You do not have permission to view AI usage." />
        </div>
      }
    >
      <div className="mx-auto max-w-5xl space-y-6 p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] sm:p-6">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <PageHeader
            title="AI usage"
            description="Token consumption and estimated costs for this organization."
          />
          <Button variant="outline" size="sm" asChild>
            <Link to="/ai/settings">AI settings</Link>
          </Button>
        </div>

        {summaryQuery.isLoading ? (
          <Skeleton className="h-24" />
        ) : summaryQuery.isError ? (
          <ErrorState message="Failed to load usage summary" onRetry={() => void summaryQuery.refetch()} />
        ) : summaryQuery.data ? (
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="rounded-lg border border-border p-4">
              <p className="text-sm text-text-muted">Tokens this month</p>
              <p className="text-2xl font-semibold">
                {summaryQuery.data.tokensThisMonth.toLocaleString()}
              </p>
            </div>
            <div className="rounded-lg border border-border p-4">
              <p className="text-sm text-text-muted">Estimated cost this month</p>
              <p className="text-2xl font-semibold">
                <Money amount={summaryQuery.data.costPaiseThisMonth} />
              </p>
            </div>
          </div>
        ) : null}

        {usageQuery.isError && (
          <ErrorState message="Failed to load usage history" onRetry={() => void usageQuery.refetch()} />
        )}

        {rows.length === 0 && !usageQuery.isLoading && (
          <EmptyState title="No usage yet" description="AI requests will appear here as your team uses assist features." />
        )}

        <ResponsiveTable
          mobile={rows.map((row) => (
            <MobileCard key={row.id}>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="font-medium">{row.taskKey}</p>
                <Badge variant="secondary" className="text-[10px]">{row.outcome}</Badge>
              </div>
              <MobileCardRow label="Feature" value={row.feature} />
              <MobileCardRow label="Provider" value={`${row.provider}/${row.model}`} />
              <MobileCardRow label="Tokens" value={row.totalTokens.toLocaleString()} />
              <MobileCardRow label="Latency" value={`${row.latencyMs} ms`} />
              <MobileCardRow label="Cost" value={<Money amount={row.costEstimatePaise} />} />
              <MobileCardRow label="When" value={<RelativeTime date={row.createdAt} />} />
              {row.piiRedacted && (
                <Badge variant="outline" className="mt-2 text-[10px]">PII redacted</Badge>
              )}
            </MobileCard>
          ))}
        >
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Task</TableHead>
                <TableHead>Feature</TableHead>
                <TableHead>Provider</TableHead>
                <TableHead className="text-right">Tokens</TableHead>
                <TableHead className="text-right">Latency</TableHead>
                <TableHead className="text-right">Cost</TableHead>
                <TableHead>Outcome</TableHead>
                <TableHead>When</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={row.id}>
                  <TableCell className="font-medium">{row.taskKey}</TableCell>
                  <TableCell>{row.feature}</TableCell>
                  <TableCell className="text-sm text-text-muted">{row.provider}/{row.model}</TableCell>
                  <TableCell className="text-right">{row.totalTokens.toLocaleString()}</TableCell>
                  <TableCell className="text-right">{row.latencyMs} ms</TableCell>
                  <TableCell className="text-right"><Money amount={row.costEstimatePaise} /></TableCell>
                  <TableCell>
                    <Badge variant="secondary" className="text-[10px]">{row.outcome}</Badge>
                  </TableCell>
                  <TableCell><RelativeTime date={row.createdAt} /></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </ResponsiveTable>

        {usageQuery.hasNextPage && (
          <Button
            variant="outline"
            disabled={usageQuery.isFetchingNextPage}
            onClick={() => void usageQuery.fetchNextPage()}
          >
            {usageQuery.isFetchingNextPage ? "Loading…" : "Load more"}
          </Button>
        )}
      </div>
    </PermissionGate>
  );
}
