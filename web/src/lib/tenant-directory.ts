import { useQuery } from "@tanstack/react-query";
import { apiRequest } from "./api-client";
import { tenantPageSchema } from "./schemas/ops";

/**
 * Organization id to name, for screens that show rows from more than one tenant.
 *
 * <p>Cross-organization views arrive holding ids and nothing else. Event log rows carry
 * `organizationId` but no name, and a column of UUIDs is unreadable — you cannot tell at a glance
 * whether the errors are all from one customer or spread across twenty, which is the entire question
 * such a view exists to answer.
 *
 * <p>Deliberately not solved on the server: the event log is a partitioned table read by the page,
 * and joining organizations into every row would cost far more than one small lookup here.
 *
 * <p>Lives in `lib/` rather than `features/ops/` because the log pages are shared by both consoles
 * and must not import the ops feature — that would pull the platform admin code into the customer
 * product's bundle. `enabled` keeps it from firing there at all.
 */
export function useTenantNames(enabled: boolean) {
  const query = useQuery({
    queryKey: ["tenant-names"],
    queryFn: () => apiRequest("/oneops/admin/platform/tenants?limit=200", tenantPageSchema),
    enabled,
    // Organization names change about never, and this is a display convenience rather than data.
    staleTime: 10 * 60 * 1000,
  });

  const names = new Map<string, string>();
  for (const tenant of query.data?.items ?? []) {
    names.set(tenant.id, tenant.name);
  }

  return {
    /** Falls back to a short id, so an unknown or still-loading tenant renders as something. */
    nameFor: (orgId: string | null | undefined) =>
      orgId ? (names.get(orgId) ?? `${orgId.slice(0, 8)}…`) : "—",
    isLoading: query.isLoading,
  };
}
