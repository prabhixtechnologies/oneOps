import { Eye } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { apiRequest } from "@/lib/api-client";
import { organizationViewSchema } from "@/lib/schemas/common";
import { useViewingOrg } from "@/lib/use-viewing-org";

/**
 * Resolves the name of the organization currently being viewed.
 *
 * <p>The handoff from the admin console carries only an id — a name in the URL would be a label
 * anybody could write, and this banner is the one thing on screen that has to be true. The request
 * carries the override header like every other, so it returns the organization being viewed.
 *
 * <p>Failure is not fatal. A banner reading "this customer" is still an accurate warning; one that
 * disappeared because a name lookup failed would not be.
 */
function useViewedOrgName(id: string | undefined) {
  const query = useQuery({
    queryKey: ["viewed-organization", id],
    queryFn: () => apiRequest(`/oneops/organizations?id=${id}`, organizationViewSchema),
    enabled: !!id,
    staleTime: 5 * 60_000,
    retry: false,
  });
  return query.data?.name;
}

/**
 * States plainly that a customer's data is on screen, whenever platform staff are inside one.
 *
 * <p>Deliberately loud, and deliberately above the content rather than tucked into a menu. Every
 * page here looks identical whichever organization is loaded — the same inbox, the same dashboard —
 * so without something unmissable it is genuinely easy to read a customer's numbers and believe
 * they are your own, or reply to a conversation from the wrong side.
 *
 * <p>Renders nothing in the ordinary case, which is almost always: an account has one organization
 * and no override is set. That includes Prabhix's own team working in their own workspace.
 */
export function ViewingOrgBanner() {
  const { viewing, isCustomerOrg, stopViewing } = useViewingOrg();
  const resolvedName = useViewedOrgName(viewing?.id);

  if (!viewing || !isCustomerOrg) return null;

  return (
    <div
      role="status"
      className="flex flex-wrap items-center gap-x-3 gap-y-1 border-b border-amber-500/40 bg-amber-500/15 px-3 py-2 text-sm lg:px-4"
    >
      <Eye className="h-4 w-4 shrink-0 text-amber-600 dark:text-amber-400" aria-hidden="true" />
      <span className="min-w-0">
        Viewing <span className="font-semibold">{resolvedName ?? viewing.name}</span> as platform
        staff. This access is recorded.
      </span>
      {/* Reloads rather than merely clearing the override. Leaving drops every query this session
          made against the customer, and a reload is the one way to be certain nothing rendered from
          them survives on screen. */}
      <Button
        size="sm"
        variant="outline"
        className="ml-auto shrink-0"
        onClick={() => {
          stopViewing();
          window.location.assign("/");
        }}
      >
        Leave
      </Button>
    </div>
  );
}
