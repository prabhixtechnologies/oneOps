import { useCallback, useEffect, useSyncExternalStore } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useAuth } from "./auth";
import { HANDOFF_PARAM } from "./staff-handoff";
import {
  setViewingOrg,
  subscribeToViewingOrg,
  viewingOrg,
  type ViewingOrg,
} from "./impersonation";

/**
 * Reads and changes which organization this app is looking at.
 *
 * <p>Normally nothing: an account has one organization and the session already names it. It is set
 * only when platform staff arrive from the admin console to support a customer, in which case every
 * request carries that organization instead and the server records the access.
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
  const homeOrgId = organization?.id ?? null;

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
    /** The organization being worked in, or null when it is simply the session's own. */
    viewing: current,
    /** Whether staff are inside an organization they are not a member of. */
    isCustomerOrg: current !== null && current.id !== homeOrgId,
    /** Only platform staff can act in an organization they are not a member of. */
    canView: me?.platformAdmin === true,
    startViewing,
    stopViewing,
  };
}

/**
 * Accepts a support handoff from the admin console: `?viewAs=<organizationId>`.
 *
 * <h2>Why the check is on the server's answer, not on the app
 *
 * <p>`me.platformAdmin` comes from `/auth/me`, so a customer cannot make it true by editing
 * anything reachable from their browser. And it is belt-and-braces regardless: the backend refuses
 * `X-Prabhix-Org` naming another organization from a non-admin token with `CROSS_TENANT_ACCESS`, so
 * the worst a customer achieves by typing this parameter is a page of failed requests. The check
 * exists to avoid that pointless state, not to be the thing that stops them.
 *
 * <p>The parameter is stripped from the address bar once consumed. Leaving it would make a
 * refresh — or a shared link — silently re-enter the customer after staff had deliberately left.
 */
export function useStaffHandoff() {
  const { me, isLoading } = useAuth();
  const { startViewing } = useViewingOrg();

  useEffect(() => {
    if (isLoading) return;

    const params = new URLSearchParams(window.location.search);
    const requested = params.get(HANDOFF_PARAM);
    if (!requested) return;

    // Consume it either way. An ignored parameter that stays in the URL invites the reader to
    // conclude it did something.
    params.delete(HANDOFF_PARAM);
    const query = params.toString();
    window.history.replaceState(
      null,
      "",
      `${window.location.pathname}${query ? `?${query}` : ""}${window.location.hash}`,
    );

    if (!me?.platformAdmin) return;
    // The organization the session already belongs to needs no override, and setting one would
    // dress ordinary work up as impersonation.
    if (requested === me.organizationId) return;

    // Named, not resolved. The tenant directory that produced this link is a staff-only endpoint,
    // and the banner is corrected as soon as the organization loads.
    startViewing({ id: requested, name: "this customer" });
  }, [isLoading, me?.platformAdmin, me?.organizationId, startViewing]);
}
