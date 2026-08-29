import { useCallback, useSyncExternalStore } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useAuth } from "./auth";
import {
  setViewingOrg,
  subscribeToViewingOrg,
  viewingOrg,
  type ViewingOrg,
} from "./impersonation";

/**
 * Reads and changes which organization the admin console is looking at.
 *
 * <p>Changing it clears the query cache. Every cached response was fetched for the previous tenant,
 * and React Query has no idea the organization is part of the request — the org travels in a header,
 * not the query key — so without this the new tenant's pages would render the old tenant's data
 * until each query happened to refetch. That is the kind of mistake that gets a support answer sent
 * to the wrong customer.
 */
export function useViewingOrg() {
  const queryClient = useQueryClient();
  const { me, organization } = useAuth();

  const current = useSyncExternalStore(subscribeToViewingOrg, viewingOrg, () => null);

  const startViewing = useCallback(
    (org: ViewingOrg) => {
      setViewingOrg(org);
      queryClient.clear();
    },
    [queryClient],
  );

  const stopViewing = useCallback(() => {
    setViewingOrg(null);
    queryClient.clear();
  }, [queryClient]);

  return {
    /** The customer organization being viewed, or null when looking at your own. */
    viewing: current,
    isViewingOther: current !== null,
    /** Only platform staff can act in an organization they are not a member of. */
    canView: me?.platformAdmin === true,
    /** Name of the organization the session itself belongs to, for the "back to" label. */
    homeOrgName: organization?.name ?? "your organization",
    startViewing,
    stopViewing,
  };
}
