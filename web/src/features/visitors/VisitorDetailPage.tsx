import { Trash2 } from "lucide-react";
import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { toast } from "sonner";
import { PageHeader } from "@/components/shared/PageHeader";
import { CursorList } from "@/components/shared/CursorList";
import { RelativeTime } from "@/components/shared/RelativeTime";
import { ErrorState } from "@/components/shared/states";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import {
  useDeleteVisitor,
  useVisitor,
  useVisitorEvents,
  useVisitorPageViews,
} from "@/features/visitors/api";
import { getApiErrorMessage } from "@/lib/api-client";
import { PERMISSIONS } from "@/lib/permissions";

export default function VisitorDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [deleteOpen, setDeleteOpen] = useState(false);
  const visitorQuery = useVisitor(id);
  const pageViewsQuery = useVisitorPageViews(id);
  const eventsQuery = useVisitorEvents(id);
  const deleteVisitor = useDeleteVisitor();

  const pageViews = pageViewsQuery.data?.pages.flatMap((p) => p.items) ?? [];
  const events = eventsQuery.data?.pages.flatMap((p) => p.items) ?? [];

  if (!id) return <ErrorState message="Visitor not found" />;
  if (visitorQuery.isLoading) return <div className="p-6"><Skeleton className="h-96" /></div>;
  if (visitorQuery.isError || !visitorQuery.data) {
    return <ErrorState message="Failed to load visitor" onRetry={() => void visitorQuery.refetch()} />;
  }

  const { visitor, sessions } = visitorQuery.data;
  const label = visitor.displayName || visitor.email || "Anonymous";

  const onDelete = async () => {
    try {
      await deleteVisitor.mutateAsync(id);
      toast.success("Visitor data deleted");
      void navigate("/visitors");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  return (
    <div className="space-y-6 p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] md:p-6">
      <PageHeader
        title={label}
        description={visitor.email ?? visitor.externalKey ?? ""}
        actions={
          <div className="flex gap-2">
            <Button variant="outline" asChild><Link to="/visitors">Back</Link></Button>
            <PermissionGate permission={PERMISSIONS.VISITOR_MANAGE}>
              <Button variant="destructive" onClick={() => setDeleteOpen(true)}>
                <Trash2 className="h-4 w-4" /> Delete data
              </Button>
            </PermissionGate>
          </div>
        }
      />

      <div className="flex flex-wrap gap-2">
        <Badge variant={visitor.identified ? "success" : "secondary"}>
          {visitor.identified ? "Identified" : "Anonymous"}
        </Badge>
        {visitor.consentStatus && <Badge variant="outline">{visitor.consentStatus}</Badge>}
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <div>
          <h2 className="mb-3 font-semibold">Sessions</h2>
          {sessions.length === 0 ? (
            <p className="text-sm text-text-muted">No sessions recorded.</p>
          ) : (
            <ul className="space-y-3">
              {sessions.map((s) => (
                <li key={s.id} className="rounded-lg border border-border p-3 text-sm">
                  <RelativeTime date={s.startedAt} className="text-xs text-text-muted" />
                  <p className="mt-1">{s.entryUrl ?? "—"}</p>
                  <p className="text-xs text-text-muted">
                    {[s.deviceType, s.browser, s.os].filter(Boolean).join(" · ")}
                    {(s.geoCity || s.geoCountry) && ` · ${[s.geoCity, s.geoCountry].filter(Boolean).join(", ")}`}
                  </p>
                  {s.referrer && <p className="mt-1 truncate text-xs">Referrer: {s.referrer}</p>}
                </li>
              ))}
            </ul>
          )}
        </div>

        <div>
          <h2 className="mb-3 font-semibold">Page views</h2>
          <div className="h-64 rounded-lg border border-border">
            <CursorList
              items={pageViews}
              hasMore={!!pageViewsQuery.hasNextPage}
              isLoading={pageViewsQuery.isLoading}
              isError={pageViewsQuery.isError}
              isFetchingNextPage={pageViewsQuery.isFetchingNextPage}
              onLoadMore={() => void pageViewsQuery.fetchNextPage()}
              getKey={(pv) => pv.id}
              emptyTitle="No page views"
              estimateSize={64}
              renderItem={(pv) => (
                <div className="border-b border-border px-4 py-2 text-sm">
                  <p className="font-medium">{pv.title || pv.path}</p>
                  <RelativeTime date={pv.viewedAt} className="text-xs text-text-muted" />
                </div>
              )}
            />
          </div>
        </div>
      </div>

      <div>
        <h2 className="mb-3 font-semibold">Events</h2>
        <div className="h-48 rounded-lg border border-border">
          <CursorList
            items={events}
            hasMore={!!eventsQuery.hasNextPage}
            isLoading={eventsQuery.isLoading}
            isError={eventsQuery.isError}
            isFetchingNextPage={eventsQuery.isFetchingNextPage}
            onLoadMore={() => void eventsQuery.fetchNextPage()}
            getKey={(e) => e.id}
            emptyTitle="No events"
            estimateSize={48}
            renderItem={(e) => (
              <div className="flex justify-between border-b border-border px-4 py-2 text-sm">
                <span>{e.name}</span>
                <RelativeTime date={e.occurredAt} className="text-xs" />
              </div>
            )}
          />
        </div>
      </div>

      <Dialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete visitor data?</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-text-muted">
            This permanently removes all data for this visitor. This action cannot be undone.
          </p>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteOpen(false)}>Cancel</Button>
            <Button variant="destructive" disabled={deleteVisitor.isPending} onClick={() => void onDelete()}>
              Delete permanently
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
