import { PageHeader } from "@/components/shared/PageHeader";
import { ErrorState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { useFeatureFlags } from "@/features/mail/api";

export default function FlagsPage() {
  const flagsQuery = useFeatureFlags();

  if (flagsQuery.isLoading) {
    return (
      <div className="space-y-6 p-6">
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-48" />
      </div>
    );
  }

  if (flagsQuery.isError || !flagsQuery.data) {
    return (
      <ErrorState message="Failed to load feature flags" onRetry={() => void flagsQuery.refetch()} />
    );
  }

  const entries = Object.entries(flagsQuery.data.flags).sort(([a], [b]) => a.localeCompare(b));

  return (
    <div className="space-y-6 p-4 md:p-6">
      <PageHeader
        title="Feature flags"
        description="Effective flags for your organization. Toggle endpoints are not exposed by the API — values are read-only."
      />
      <div className="divide-y divide-border rounded-lg border border-border">
        {entries.length === 0 ? (
          <p className="p-6 text-sm text-text-muted">No feature flags configured.</p>
        ) : (
          entries.map(([key, enabled]) => (
            <div key={key} className="flex items-center justify-between gap-4 px-4 py-3">
              <div>
                <p className="font-mono text-sm">{key}</p>
                <Badge variant={enabled ? "success" : "secondary"} className="mt-1">
                  {enabled ? "Enabled" : "Disabled"}
                </Badge>
              </div>
              <Switch checked={enabled} disabled aria-label={`${key} flag`} />
            </div>
          ))
        )}
      </div>
    </div>
  );
}
