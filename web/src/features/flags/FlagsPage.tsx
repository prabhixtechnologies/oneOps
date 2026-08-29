import { toast } from "sonner";
import { PageHeader } from "@/components/shared/PageHeader";
import { ErrorState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { useAuth } from "@/lib/auth";
import { getApiErrorMessage } from "@/lib/api-client";
import { PERMISSIONS, hasPermission } from "@/lib/permissions";
import { useFeatureFlags, useSetFeatureFlag } from "@/features/mail/api";

export default function FlagsPage() {
  const flagsQuery = useFeatureFlags();
  const setFlag = useSetFeatureFlag();
  const { permissions } = useAuth();
  const canEdit = hasPermission(permissions, PERMISSIONS.ORG_UPDATE);

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

  const flags = [...flagsQuery.data.flags].sort((a, b) => a.key.localeCompare(b.key));

  const handleToggle = (key: string, enabled: boolean) => {
    setFlag.mutate(
      { key, enabled },
      {
        onSuccess: () => toast.success(`${key} ${enabled ? "enabled" : "disabled"}`),
        onError: (err) => toast.error(getApiErrorMessage(err)),
      },
    );
  };

  return (
    <div className="space-y-6 p-4 md:p-6">
      <PageHeader
        title="Feature flags"
        description={
          canEdit
            ? "Flags in effect for your organization. Changing one stores an override for this organization only."
            : "Flags in effect for your organization. Changing them requires the organization update permission."
        }
      />
      <div className="divide-y divide-border rounded-lg border border-border">
        {flags.length === 0 ? (
          <p className="p-6 text-sm text-text-muted">No feature flags configured.</p>
        ) : (
          flags.map((flag) => (
            <div key={flag.key} className="flex items-center justify-between gap-4 px-4 py-3">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <p className="font-mono text-sm">{flag.key}</p>
                  {flag.source === "OVERRIDE" && <Badge variant="secondary">Override</Badge>}
                </div>
                {flag.description && (
                  <p className="mt-1 text-sm text-text-muted">{flag.description}</p>
                )}
              </div>
              <Switch
                checked={flag.enabled}
                disabled={!canEdit || setFlag.isPending}
                onCheckedChange={(next) => handleToggle(flag.key, next)}
                aria-label={`${flag.key} flag`}
              />
            </div>
          ))
        )}
      </div>
    </div>
  );
}
