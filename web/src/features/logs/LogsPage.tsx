import { useMemo, useState, type ReactNode } from "react";
import { Download, Search } from "lucide-react";
import { PageHeader } from "@/components/shared/PageHeader";
import { CursorList } from "@/components/shared/CursorList";
import { MobileCard, MobileCardRow } from "@/components/shared/ResponsiveTable";
import { RelativeTime } from "@/components/shared/RelativeTime";
import { ErrorState } from "@/components/shared/states";
import { MiniBarChart, Sparkline } from "@/components/shared/charts";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { PERMISSIONS } from "@/lib/permissions";
import type { EventLogEntry } from "@/lib/schemas/logs";
import {
  exportEventLogs,
  useEventLogStats,
  useEventLogTrace,
  useEventLogs,
} from "@/features/logs/api";
import { cn } from "@/lib/utils";

const SEVERITIES = ["all", "DEBUG", "INFO", "WARN", "ERROR", "FATAL"] as const;
const CATEGORIES = [
  "all", "AUTH", "ORG", "MAIL", "BILLING", "COMMERCE", "CHAT",
  "VISITOR", "FILE", "AI", "JOB", "INTEGRATION", "SECURITY", "PLATFORM",
] as const;

function severityClass(severity: string) {
  switch (severity) {
    case "ERROR":
    case "FATAL":
      return "bg-destructive/15 text-destructive border-destructive/30";
    case "WARN":
      return "bg-amber-500/15 text-amber-700 dark:text-amber-400 border-amber-500/30";
    case "INFO":
      return "bg-primary/10 text-primary border-primary/20";
    default:
      return "bg-muted text-text-muted border-border";
  }
}

export default function LogsPage() {
  const [severity, setSeverity] = useState<string | undefined>();
  const [category, setCategory] = useState<string | undefined>();
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<EventLogEntry | null>(null);
  const [traceId, setTraceId] = useState<string | undefined>();

  const filters = useMemo(
    () => ({
      severity: severity && severity !== "all" ? severity : undefined,
      category: category && category !== "all" ? category : undefined,
      search: search.trim() || undefined,
    }),
    [severity, category, search],
  );

  const logsQuery = useEventLogs(filters);
  const statsQuery = useEventLogStats();
  const traceQuery = useEventLogTrace(traceId);

  const logs = logsQuery.data?.pages.flatMap((p) => p.items) ?? [];

  if (logsQuery.isError) {
    return (
      <ErrorState
        message="Failed to load event logs"
        onRetry={() => void logsQuery.refetch()}
      />
    );
  }

  return (
    <div className="space-y-6 p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] md:p-6">
      <PageHeader
        title="Event logs"
        description="Operational events across your organization — searchable, correlated, exportable"
        actions={
          <PermissionGate permission={PERMISSIONS.LOG_EXPORT}>
            <Button
              variant="outline"
              size="sm"
              className="gap-2"
              onClick={() => void exportEventLogs("csv", filters)}
            >
              <Download className="h-4 w-4" />
              Export
            </Button>
          </PermissionGate>
        }
      />

      {statsQuery.data && (
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="rounded-lg border border-border p-4">
            <p className="mb-2 text-sm font-medium">Errors (24h)</p>
            <Sparkline
              data={statsQuery.data.errorsOverTime.map((b) => b.errorCount)}
              className="w-full max-w-full"
            />
          </div>
          <div className="rounded-lg border border-border p-4">
            <p className="mb-2 text-sm font-medium">Top event codes</p>
            <MiniBarChart
              data={statsQuery.data.topEventCodes.slice(0, 6).map((e) => ({
                label: e.eventCode.split(".").pop() ?? e.eventCode,
                value: e.count,
              }))}
              width={280}
              className="h-12 w-full max-w-[280px]"
            />
          </div>
        </div>
      )}

      <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center">
        <div className="relative flex-1 min-w-[200px]">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-text-muted" />
          <Input
            placeholder="Search payload…"
            className="pl-9"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <Select value={severity ?? "all"} onValueChange={(v) => setSeverity(v)}>
          <SelectTrigger className="w-full sm:w-36">
            <SelectValue placeholder="Severity" />
          </SelectTrigger>
          <SelectContent>
            {SEVERITIES.map((s) => (
              <SelectItem key={s} value={s}>{s === "all" ? "All severities" : s}</SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={category ?? "all"} onValueChange={(v) => setCategory(v)}>
          <SelectTrigger className="w-full sm:w-40">
            <SelectValue placeholder="Category" />
          </SelectTrigger>
          <SelectContent>
            {CATEGORIES.map((c) => (
              <SelectItem key={c} value={c}>{c === "all" ? "All categories" : c}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="h-[min(600px,70vh)] rounded-lg border border-border">
        <CursorList<EventLogEntry>
          items={logs}
          hasMore={!!logsQuery.hasNextPage}
          isLoading={logsQuery.isLoading}
          isError={logsQuery.isError}
          isFetchingNextPage={logsQuery.isFetchingNextPage}
          onLoadMore={() => void logsQuery.fetchNextPage()}
          getKey={(l) => l.id}
          emptyTitle="No events"
          estimateSize={56}
          renderItem={(log) => (
            <button
              type="button"
              className="w-full text-left"
              onClick={() => {
                setSelected(log);
                setTraceId(undefined);
              }}
            >
              <div className="hidden items-center justify-between gap-4 border-b border-border px-4 py-3 text-sm md:flex">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <Badge variant="outline" className={cn("shrink-0", severityClass(log.severity))}>
                      {log.severity}
                    </Badge>
                    <p className="truncate font-medium">{log.eventCode}</p>
                  </div>
                  <p className="truncate text-text-muted">
                    {log.actorLabel ?? log.actorType} · {log.category}
                    {log.correlationId && (
                      <span className="ml-2 font-mono text-xs">{log.correlationId.slice(0, 8)}…</span>
                    )}
                  </p>
                </div>
                <RelativeTime date={log.occurredAt} className="shrink-0 text-xs" />
              </div>
              <div className="p-2 md:hidden">
                <MobileCard>
                  <div className="flex items-center justify-between gap-2">
                    <Badge variant="outline" className={severityClass(log.severity)}>{log.severity}</Badge>
                    <RelativeTime date={log.occurredAt} className="text-xs" />
                  </div>
                  <p className="font-medium">{log.eventCode}</p>
                  <MobileCardRow label="Actor" value={log.actorLabel ?? log.actorType} />
                </MobileCard>
              </div>
            </button>
          )}
        />
      </div>

      {selected && (
        <Dialog open={!!selected} onOpenChange={(open) => !open && setSelected(null)}>
          <DialogContent className="max-h-[90vh] w-full overflow-y-auto sm:max-w-lg">
            <DialogHeader>
              <DialogTitle className="font-mono text-sm">{selected.eventCode}</DialogTitle>
            </DialogHeader>
            <div className="mt-4 space-y-3 text-sm">
              <DetailRow label="Severity" value={selected.severity} />
              <DetailRow label="Category" value={selected.category} />
              <DetailRow label="Correlation" value={selected.correlationId} mono />
              <DetailRow label="Actor" value={selected.actorLabel ?? selected.actorType} />
              <DetailRow label="When" value={<RelativeTime date={selected.occurredAt} />} />
              {selected.payload && Object.keys(selected.payload).length > 0 && (
                <pre className="max-h-64 overflow-auto rounded-md bg-muted p-3 text-xs">
                  {JSON.stringify(selected.payload, null, 2)}
                </pre>
              )}
              <Button
                variant="secondary"
                size="sm"
                onClick={() => setTraceId(selected.correlationId)}
              >
                View full trace
              </Button>
              {traceId === selected.correlationId && traceQuery.data && (
                <div className="space-y-2 border-t border-border pt-3">
                  <p className="font-medium">Trace ({traceQuery.data.entries.length} entries)</p>
                  {traceQuery.data.entries.map((entry, i) => (
                    <div key={`${entry.source}-${i}`} className="rounded border border-border p-2 text-xs">
                      <p className="font-medium">{entry.source}: {entry.actionOrCode}</p>
                      <RelativeTime date={entry.timestamp} />
                    </div>
                  ))}
                </div>
              )}
            </div>
          </DialogContent>
        </Dialog>
      )}
    </div>
  );
}

function DetailRow({
  label,
  value,
  mono,
}: {
  label: string;
  value: ReactNode;
  mono?: boolean;
}) {
  return (
    <div className="flex flex-col gap-0.5 sm:flex-row sm:gap-4">
      <span className="w-28 shrink-0 text-text-muted">{label}</span>
      <span className={cn("break-all", mono && "font-mono text-xs")}>{value}</span>
    </div>
  );
}
