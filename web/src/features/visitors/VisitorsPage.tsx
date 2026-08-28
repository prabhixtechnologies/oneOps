import { Activity, Globe, Users } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router";
import { PageHeader } from "@/components/shared/PageHeader";
import { CursorList } from "@/components/shared/CursorList";
import { MobileCard, MobileCardRow } from "@/components/shared/ResponsiveTable";
import { RelativeTime } from "@/components/shared/RelativeTime";
import { EmptyState, ErrorState } from "@/components/shared/states";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type { LiveVisitor, VisitorSummary } from "@/lib/schemas/visitor";
import { useLiveVisitors, useVisitorAnalytics, useVisitors } from "@/features/visitors/api";
import { PERMISSIONS } from "@/lib/permissions";

function LiveVisitorCard({ visitor }: { visitor: LiveVisitor }) {
  const label = visitor.displayName || visitor.email || visitor.externalKey || "Anonymous";
  return (
    <MobileCard>
      <p className="font-medium">{label}</p>
      <MobileCardRow label="Page" value={visitor.currentTitle || visitor.currentPath || "—"} />
      <MobileCardRow label="Since" value={<RelativeTime date={visitor.since} className="text-xs" />} />
      <Button variant="outline" size="sm" className="mt-3 w-full" asChild>
        <Link to={`/visitors/${visitor.visitorId}`}>View profile</Link>
      </Button>
    </MobileCard>
  );
}

function VisitorDirectoryCard({ visitor }: { visitor: VisitorSummary }) {
  const label = visitor.displayName || visitor.email || "Anonymous";
  return (
    <MobileCard>
      <div className="flex items-start justify-between gap-2">
        <p className="font-medium">{label}</p>
        <Badge variant={visitor.identified ? "success" : "secondary"}>
          {visitor.identified ? "Identified" : "Anonymous"}
        </Badge>
      </div>
      {visitor.email && <p className="text-text-muted">{visitor.email}</p>}
      <MobileCardRow label="Last seen" value={<RelativeTime date={visitor.lastSeenAt} className="text-xs" />} />
      <Button variant="outline" size="sm" className="mt-3 w-full" asChild>
        <Link to={`/visitors/${visitor.id}`}>View profile</Link>
      </Button>
    </MobileCard>
  );
}

function AnalyticsPanel() {
  const analyticsQuery = useVisitorAnalytics(30);

  if (analyticsQuery.isLoading) return <Skeleton className="h-48" />;
  if (analyticsQuery.isError) {
    return <ErrorState message="Failed to load analytics" onRetry={() => void analyticsQuery.refetch()} />;
  }

  const data = analyticsQuery.data;
  const hasTotals =
    (data?.totalVisitors ?? 0) > 0 ||
    (data?.identifiedVisitors ?? 0) > 0 ||
    (data?.chatConversions ?? 0) > 0;

  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-3">
        <div className="rounded-lg border border-border p-4">
          <p className="text-xs uppercase text-text-muted">Total visitors</p>
          <p className="text-2xl font-semibold">{data?.totalVisitors ?? 0}</p>
        </div>
        <div className="rounded-lg border border-border p-4">
          <p className="text-xs uppercase text-text-muted">Identified</p>
          <p className="text-2xl font-semibold">{data?.identifiedVisitors ?? 0}</p>
        </div>
        <div className="rounded-lg border border-border p-4">
          <p className="text-xs uppercase text-text-muted">Chat conversions</p>
          <p className="text-2xl font-semibold">{data?.chatConversions ?? 0}</p>
        </div>
      </div>

      {!hasTotals && !(data?.topPages?.length) && !(data?.topReferrers?.length) && !(data?.sessionsOverTime?.length) && (
        <EmptyState title="No analytics data yet" description="Data will appear once visitors browse your site." />
      )}

      {(data?.topPages?.length ?? 0) > 0 && (
        <div>
          <h3 className="mb-2 font-medium">Top pages</h3>
          <ul className="divide-y divide-border rounded-lg border border-border">
            {data?.topPages?.map((row) => (
              <li key={row.dimension} className="flex justify-between px-4 py-2 text-sm">
                <span className="truncate">{row.dimension}</span>
                <span className="text-text-muted">{row.count}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {(data?.topReferrers?.length ?? 0) > 0 && (
        <div>
          <h3 className="mb-2 font-medium">Top referrers</h3>
          <ul className="divide-y divide-border rounded-lg border border-border">
            {data?.topReferrers?.map((row) => (
              <li key={row.dimension} className="flex justify-between px-4 py-2 text-sm">
                <span className="truncate">{row.dimension || "(direct)"}</span>
                <span className="text-text-muted">{row.count}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {(data?.sessionsOverTime?.length ?? 0) > 0 && (
        <div>
          <h3 className="mb-2 font-medium">Sessions over time</h3>
          <ul className="divide-y divide-border rounded-lg border border-border">
            {data?.sessionsOverTime?.map((row) => (
              <li key={row.date} className="flex justify-between px-4 py-2 text-sm">
                <span>{row.date}</span>
                <span className="text-text-muted">{row.count}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

export default function VisitorsPage() {
  const [search, setSearch] = useState("");
  const liveQuery = useLiveVisitors();
  const visitorsQuery = useVisitors(search);

  const visitors = visitorsQuery.data?.pages.flatMap((p) => p.items) ?? [];

  return (
    <div className="space-y-6 p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] md:p-6">
      <PageHeader
        title="Visitors"
        description="Live presence, visitor directory, and analytics"
      />

      <Tabs defaultValue="live">
        <TabsList className="w-full overflow-x-auto">
          <TabsTrigger value="live" className="gap-1"><Activity className="h-3 w-3" /> Live</TabsTrigger>
          <TabsTrigger value="directory" className="gap-1"><Users className="h-3 w-3" /> Directory</TabsTrigger>
          <PermissionGate permission={PERMISSIONS.VISITOR_ANALYTICS}>
            <TabsTrigger value="analytics" className="gap-1"><Globe className="h-3 w-3" /> Analytics</TabsTrigger>
          </PermissionGate>
        </TabsList>

        <TabsContent value="live" className="mt-4">
          {liveQuery.isLoading ? (
            <Skeleton className="h-48" />
          ) : liveQuery.isError ? (
            <ErrorState message="Failed to load live visitors" onRetry={() => void liveQuery.refetch()} />
          ) : (liveQuery.data?.length ?? 0) === 0 ? (
            <EmptyState title="No one online" description="Visitors currently on your site will appear here." />
          ) : (
            <>
              <p className="mb-3 text-xs text-text-muted">Refreshes every 15 seconds</p>
              <div className="hidden rounded-lg border border-border md:block">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Visitor</TableHead>
                      <TableHead>Current page</TableHead>
                      <TableHead>Since</TableHead>
                      <TableHead />
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {liveQuery.data?.map((v) => {
                      const label = v.displayName || v.email || v.externalKey || "Anonymous";
                      return (
                        <TableRow key={v.visitorId}>
                          <TableCell className="font-medium">{label}</TableCell>
                          <TableCell>{v.currentTitle || v.currentPath || "—"}</TableCell>
                          <TableCell><RelativeTime date={v.since} className="text-xs" /></TableCell>
                          <TableCell>
                            <Button variant="outline" size="sm" asChild>
                              <Link to={`/visitors/${v.visitorId}`}>View</Link>
                            </Button>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </div>
              <div className="space-y-3 md:hidden">
                {liveQuery.data?.map((v) => <LiveVisitorCard key={v.visitorId} visitor={v} />)}
              </div>
            </>
          )}
        </TabsContent>

        <TabsContent value="directory" className="mt-4">
          <Input
            placeholder="Search visitors…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="mb-4 max-w-sm"
            aria-label="Search visitors"
          />
          <div className="h-[500px] rounded-lg border border-border">
            <CursorList
              items={visitors}
              hasMore={!!visitorsQuery.hasNextPage}
              isLoading={visitorsQuery.isLoading}
              isError={visitorsQuery.isError}
              isFetchingNextPage={visitorsQuery.isFetchingNextPage}
              onLoadMore={() => void visitorsQuery.fetchNextPage()}
              getKey={(v) => v.id}
              emptyTitle="No visitors found"
              estimateSize={72}
              renderItem={(v) => {
                const label = v.displayName || v.email || "Anonymous";
                return (
                  <>
                    <div className="hidden items-center justify-between border-b border-border px-4 py-3 text-sm md:flex">
                      <div>
                        <p className="font-medium">{label}</p>
                        <p className="text-text-muted">{v.email}</p>
                      </div>
                      <div className="flex items-center gap-2">
                        <Badge variant={v.identified ? "success" : "secondary"}>
                          {v.identified ? "Identified" : "Anonymous"}
                        </Badge>
                        <RelativeTime date={v.lastSeenAt} className="text-xs" />
                        <Button variant="outline" size="sm" asChild>
                          <Link to={`/visitors/${v.id}`}>View</Link>
                        </Button>
                      </div>
                    </div>
                    <div className="p-2 md:hidden">
                      <VisitorDirectoryCard visitor={v} />
                    </div>
                  </>
                );
              }}
            />
          </div>
        </TabsContent>

        <TabsContent value="analytics" className="mt-4">
          <AnalyticsPanel />
        </TabsContent>
      </Tabs>
    </div>
  );
}
