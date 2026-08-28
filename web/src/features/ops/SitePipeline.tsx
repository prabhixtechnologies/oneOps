import { useEffect, useState } from "react";
import { toast } from "sonner";
import { CursorList } from "@/components/shared/CursorList";
import { MobileCard, MobileCardRow } from "@/components/shared/ResponsiveTable";
import { RelativeTime } from "@/components/shared/RelativeTime";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { StatusBadge } from "@/features/ops/OpsHubPage";
import { StatusFilter } from "@/features/ops/StatusFilter";
import {
  useApplication,
  useApplications,
  useLead,
  useLeads,
  useSubscribers,
  useUpdateApplication,
  useUpdateLead,
} from "@/features/ops/api";
import {
  APPLICATION_STATUSES,
  LEAD_STATUSES,
  SUBSCRIBER_STATUSES,
  type ApplicationSummary,
  type LeadSummary,
  type SubscriberSummary,
} from "@/lib/schemas/ops";

export function LeadsTab() {
  const [status, setStatus] = useState<string | undefined>();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const query = useLeads(status);
  const leads = query.data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <div className="space-y-4">
      <StatusFilter
        label="All statuses"
        statuses={LEAD_STATUSES}
        value={status}
        onChange={setStatus}
      />

      <div className="h-[min(600px,70vh)] rounded-lg border border-border">
        <CursorList<LeadSummary>
          items={leads}
          hasMore={!!query.hasNextPage}
          isLoading={query.isLoading}
          isError={query.isError}
          errorMessage="Failed to load leads"
          isFetchingNextPage={query.isFetchingNextPage}
          onLoadMore={() => void query.fetchNextPage()}
          getKey={(lead) => lead.id}
          emptyTitle="No leads"
          emptyDescription="Enquiries from the marketing site land here."
          estimateSize={64}
          renderItem={(lead) => (
            <button
              type="button"
              className="w-full text-left"
              onClick={() => setSelectedId(lead.id)}
            >
              <div className="hidden items-center gap-4 border-b border-border px-4 py-3 text-sm md:flex">
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium">{lead.name ?? lead.email}</p>
                  <p className="truncate text-text-muted">
                    {lead.email}
                    {lead.company && ` · ${lead.company}`}
                  </p>
                </div>
                {lead.interest && (
                  <span className="hidden w-32 shrink-0 truncate text-xs text-text-muted lg:block">
                    {lead.interest.replace(/_/g, " ")}
                  </span>
                )}
                <StatusBadge status={lead.status} />
                <RelativeTime date={lead.createdAt} className="w-28 shrink-0 text-right text-xs" />
              </div>
              <div className="p-2 md:hidden">
                <MobileCard>
                  <div className="flex items-center justify-between gap-2">
                    <p className="min-w-0 truncate font-medium">{lead.name ?? lead.email}</p>
                    <StatusBadge status={lead.status} />
                  </div>
                  <MobileCardRow label="Email" value={lead.email} />
                  {lead.company && <MobileCardRow label="Company" value={lead.company} />}
                  <MobileCardRow label="Received" value={<RelativeTime date={lead.createdAt} />} />
                </MobileCard>
              </div>
            </button>
          )}
        />
      </div>

      <LeadDialog id={selectedId} onClose={() => setSelectedId(null)} />
    </div>
  );
}

function LeadDialog({ id, onClose }: { id: string | null; onClose: () => void }) {
  const { data, isLoading } = useLead(id ?? undefined);
  const update = useUpdateLead();
  const [status, setStatus] = useState<string>("");
  const [notes, setNotes] = useState("");

  // Reset when a different lead is opened, otherwise the previous lead's edits leak across.
  useEffect(() => {
    setStatus(data?.status ?? "");
    setNotes(data?.internalNotes ?? "");
  }, [data?.id, data?.status, data?.internalNotes]);

  const save = () => {
    if (!id) return;
    update.mutate(
      { id, status, internalNotes: notes },
      {
        onSuccess: () => toast.success("Lead updated"),
        onError: (error) => toast.error(error instanceof Error ? error.message : "Update failed"),
      },
    );
  };

  return (
    <Dialog open={!!id} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[90vh] w-full overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{data?.name ?? data?.email ?? "Lead"}</DialogTitle>
        </DialogHeader>
        {isLoading || !data ? (
          <Skeleton className="h-48" />
        ) : (
          <div className="mt-4 space-y-3 text-sm">
            <DetailRow label="Email" value={data.email} />
            {data.phone && <DetailRow label="Phone" value={data.phone} />}
            {data.company && <DetailRow label="Company" value={data.company} />}
            {data.employeeCount && <DetailRow label="Size" value={data.employeeCount} />}
            {data.interest && (
              <DetailRow label="Interest" value={data.interest.replace(/_/g, " ")} />
            )}
            {data.source && <DetailRow label="Source" value={data.source} />}
            {data.referrer && <DetailRow label="Referrer" value={data.referrer} />}
            <DetailRow label="Received" value={<RelativeTime date={data.createdAt} />} />
            {data.contactedAt && (
              <DetailRow label="Contacted" value={<RelativeTime date={data.contactedAt} />} />
            )}
            {data.message && (
              <div>
                <p className="mb-1 text-text-muted">Message</p>
                <p className="whitespace-pre-wrap rounded-md bg-muted p-3">{data.message}</p>
              </div>
            )}

            <StatusEditor
              statuses={LEAD_STATUSES}
              status={status}
              onStatusChange={setStatus}
              notes={notes}
              onNotesChange={setNotes}
              onSave={save}
              isSaving={update.isPending}
            />
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}

export function SubscribersTab() {
  const [status, setStatus] = useState<string | undefined>();
  const query = useSubscribers(status);
  const subscribers = query.data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <div className="space-y-4">
      <StatusFilter
        label="All statuses"
        statuses={SUBSCRIBER_STATUSES}
        value={status}
        onChange={setStatus}
      />

      <div className="h-[min(600px,70vh)] rounded-lg border border-border">
        <CursorList<SubscriberSummary>
          items={subscribers}
          hasMore={!!query.hasNextPage}
          isLoading={query.isLoading}
          isError={query.isError}
          errorMessage="Failed to load subscribers"
          isFetchingNextPage={query.isFetchingNextPage}
          onLoadMore={() => void query.fetchNextPage()}
          getKey={(subscriber) => subscriber.id}
          emptyTitle="No subscribers"
          emptyDescription="Newsletter sign-ups from the marketing site land here."
          estimateSize={56}
          renderItem={(subscriber) => (
            <>
              <div className="hidden items-center gap-4 border-b border-border px-4 py-3 text-sm md:flex">
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium">{subscriber.email}</p>
                  {subscriber.name && (
                    <p className="truncate text-text-muted">{subscriber.name}</p>
                  )}
                </div>
                <StatusBadge status={subscriber.status} />
                <RelativeTime
                  date={subscriber.createdAt}
                  className="w-28 shrink-0 text-right text-xs"
                />
              </div>
              <div className="p-2 md:hidden">
                <MobileCard>
                  <div className="flex items-center justify-between gap-2">
                    <p className="min-w-0 truncate font-medium">{subscriber.email}</p>
                    <StatusBadge status={subscriber.status} />
                  </div>
                  <MobileCardRow
                    label="Subscribed"
                    value={<RelativeTime date={subscriber.createdAt} />}
                  />
                </MobileCard>
              </div>
            </>
          )}
        />
      </div>
    </div>
  );
}

export function ApplicationsTab() {
  const [status, setStatus] = useState<string | undefined>();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const query = useApplications(status);
  const applications = query.data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <div className="space-y-4">
      <StatusFilter
        label="All statuses"
        statuses={APPLICATION_STATUSES}
        value={status}
        onChange={setStatus}
      />

      <div className="h-[min(600px,70vh)] rounded-lg border border-border">
        <CursorList<ApplicationSummary>
          items={applications}
          hasMore={!!query.hasNextPage}
          isLoading={query.isLoading}
          isError={query.isError}
          errorMessage="Failed to load applications"
          isFetchingNextPage={query.isFetchingNextPage}
          onLoadMore={() => void query.fetchNextPage()}
          getKey={(application) => application.id}
          emptyTitle="No applications"
          emptyDescription="Applications for your open roles land here."
          estimateSize={64}
          renderItem={(application) => (
            <button
              type="button"
              className="w-full text-left"
              onClick={() => setSelectedId(application.id)}
            >
              <div className="hidden items-center gap-4 border-b border-border px-4 py-3 text-sm md:flex">
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium">{application.name ?? application.email}</p>
                  <p className="truncate text-text-muted">{application.email}</p>
                </div>
                <span className="hidden w-40 shrink-0 truncate font-mono text-xs text-text-muted lg:block">
                  {application.roleSlug}
                </span>
                <StatusBadge status={application.status} />
                <RelativeTime
                  date={application.createdAt}
                  className="w-28 shrink-0 text-right text-xs"
                />
              </div>
              <div className="p-2 md:hidden">
                <MobileCard>
                  <div className="flex items-center justify-between gap-2">
                    <p className="min-w-0 truncate font-medium">
                      {application.name ?? application.email}
                    </p>
                    <StatusBadge status={application.status} />
                  </div>
                  <MobileCardRow label="Role" value={application.roleSlug} />
                  <MobileCardRow label="Email" value={application.email} />
                  <MobileCardRow
                    label="Applied"
                    value={<RelativeTime date={application.createdAt} />}
                  />
                </MobileCard>
              </div>
            </button>
          )}
        />
      </div>

      <ApplicationDialog id={selectedId} onClose={() => setSelectedId(null)} />
    </div>
  );
}

function ApplicationDialog({ id, onClose }: { id: string | null; onClose: () => void }) {
  const { data, isLoading } = useApplication(id ?? undefined);
  const update = useUpdateApplication();
  const [status, setStatus] = useState<string>("");
  const [notes, setNotes] = useState("");

  useEffect(() => {
    setStatus(data?.status ?? "");
    setNotes(data?.internalNotes ?? "");
  }, [data?.id, data?.status, data?.internalNotes]);

  const save = () => {
    if (!id) return;
    update.mutate(
      { id, status, internalNotes: notes },
      {
        onSuccess: () => toast.success("Application updated"),
        onError: (error) => toast.error(error instanceof Error ? error.message : "Update failed"),
      },
    );
  };

  return (
    <Dialog open={!!id} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[90vh] w-full overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{data?.name ?? data?.email ?? "Application"}</DialogTitle>
        </DialogHeader>
        {isLoading || !data ? (
          <Skeleton className="h-48" />
        ) : (
          <div className="mt-4 space-y-3 text-sm">
            <DetailRow label="Role" value={data.roleSlug} />
            <DetailRow label="Email" value={data.email} />
            {data.phone && <DetailRow label="Phone" value={data.phone} />}
            {data.portfolioUrl && <DetailRow label="Portfolio" value={data.portfolioUrl} />}
            {data.linkedinUrl && <DetailRow label="LinkedIn" value={data.linkedinUrl} />}
            <DetailRow label="Applied" value={<RelativeTime date={data.createdAt} />} />
            {data.coverLetter && (
              <div>
                <p className="mb-1 text-text-muted">Cover letter</p>
                <p className="max-h-48 overflow-y-auto whitespace-pre-wrap rounded-md bg-muted p-3">
                  {data.coverLetter}
                </p>
              </div>
            )}
            {data.resumeFileId && (
              <DetailRow
                label="Résumé"
                value={<span className="font-mono text-xs">{data.resumeFileId}</span>}
              />
            )}

            <StatusEditor
              statuses={APPLICATION_STATUSES}
              status={status}
              onStatusChange={setStatus}
              notes={notes}
              onNotesChange={setNotes}
              onSave={save}
              isSaving={update.isPending}
            />
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}

function StatusEditor({
  statuses,
  status,
  onStatusChange,
  notes,
  onNotesChange,
  onSave,
  isSaving,
}: {
  statuses: readonly string[];
  status: string;
  onStatusChange: (status: string) => void;
  notes: string;
  onNotesChange: (notes: string) => void;
  onSave: () => void;
  isSaving: boolean;
}) {
  return (
    <div className="space-y-3 border-t border-border pt-4">
      <Select value={status} onValueChange={onStatusChange}>
        <SelectTrigger>
          <SelectValue placeholder="Status" />
        </SelectTrigger>
        <SelectContent>
          {statuses.map((option) => (
            <SelectItem key={option} value={option}>
              {option.replace(/_/g, " ")}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <Textarea
        placeholder="Internal notes (not visible to the applicant)"
        value={notes}
        onChange={(event) => onNotesChange(event.target.value)}
        rows={3}
      />
      <Button size="sm" onClick={onSave} disabled={isSaving || !status}>
        {isSaving ? "Saving…" : "Save"}
      </Button>
    </div>
  );
}

function DetailRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5 sm:flex-row sm:gap-4">
      <span className="w-24 shrink-0 text-text-muted">{label}</span>
      <span className="break-all">{value}</span>
    </div>
  );
}
