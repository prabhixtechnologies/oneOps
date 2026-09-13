import { useState } from "react";
import { PageHeader } from "@/components/shared/PageHeader";
import { CursorList } from "@/components/shared/CursorList";
import { MobileCard, MobileCardRow } from "@/components/shared/ResponsiveTable";
import { RelativeTime } from "@/components/shared/RelativeTime";
import { ErrorState } from "@/components/shared/states";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { AuditLogEntry } from "@/lib/schemas/org";
import { useAuditLogs } from "@/features/org/api";

const ACTION_FILTERS = [
  { value: "all", label: "All actions" },
  { value: "member.invited", label: "Member invited" },
  { value: "role.updated", label: "Role updated" },
];

export default function AuditPage() {
  const [action, setAction] = useState<string | undefined>();
  const auditQuery = useAuditLogs(action);

  const logs = auditQuery.data?.pages.flatMap((p) => p.items) ?? [];

  if (auditQuery.isError) {
    return <ErrorState message="Failed to load audit log" onRetry={() => void auditQuery.refetch()} />;
  }

  return (
    <div className="flex min-h-full flex-col space-y-6 p-6">
      <PageHeader
        title="Audit log"
        description="Append-only record of security-relevant actions in your organization"
      />

      <Select
        value={action ?? "all"}
        onValueChange={(v) => setAction(v === "all" ? undefined : v)}
      >
        <SelectTrigger className="w-48">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {ACTION_FILTERS.map((f) => (
            <SelectItem key={f.value} value={f.value}>{f.label}</SelectItem>
          ))}
        </SelectContent>
      </Select>

      <div className="min-h-[12rem] flex-1 rounded-lg border border-border">
        <CursorList<AuditLogEntry>
          items={logs}
          hasMore={!!auditQuery.hasNextPage}
          isLoading={auditQuery.isLoading}
          isError={auditQuery.isError}
          isFetchingNextPage={auditQuery.isFetchingNextPage}
          onLoadMore={() => void auditQuery.fetchNextPage()}
          getKey={(l) => l.id}
          emptyTitle="No audit entries"
          estimateSize={52}
          renderItem={(log) => (
            <>
              <div className="hidden items-center justify-between border-b border-border px-4 py-3 text-sm md:flex">
                <div>
                  <p className="font-medium">{log.action}</p>
                  <p className="text-text-muted">
                    {log.actor?.name ?? log.actorLabel ?? "System"} · {log.resource ?? log.resourceType}
                    {log.resourceId && ` #${log.resourceId}`}
                  </p>
                </div>
                <div className="text-right">
                  <RelativeTime date={log.createdAt} className="text-xs" />
                  {log.ipAddress && <p className="text-xs text-text-muted">{log.ipAddress}</p>}
                </div>
              </div>
              <div className="p-2 md:hidden">
                <MobileCard>
                  <p className="font-medium">{log.action}</p>
                  <MobileCardRow label="Actor" value={log.actor?.name ?? log.actorLabel ?? "System"} />
                  <MobileCardRow label="When" value={<RelativeTime date={log.createdAt} className="text-xs" />} />
                </MobileCard>
              </div>
            </>
          )}
        />
      </div>
    </div>
  );
}
