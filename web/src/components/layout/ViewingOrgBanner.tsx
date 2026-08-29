import { Eye } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useViewingOrg } from "@/lib/use-viewing-org";

/**
 * States plainly whose data is on screen while staff are inside a customer's organization.
 *
 * <p>Deliberately loud, and deliberately above the content rather than tucked into a menu. Every
 * page in this console looks identical whichever tenant is loaded — the same inbox, the same
 * dashboard — so without something unmissable it is genuinely easy to read a customer's numbers and
 * believe they are your own, or reply to a conversation from the wrong side.
 */
export function ViewingOrgBanner() {
  const { viewing, homeOrgName, stopViewing } = useViewingOrg();

  if (!viewing) return null;

  return (
    <div
      role="status"
      className="flex flex-wrap items-center gap-x-3 gap-y-1 border-b border-amber-500/40 bg-amber-500/15 px-3 py-2 text-sm lg:px-4"
    >
      <Eye className="h-4 w-4 shrink-0 text-amber-600 dark:text-amber-400" aria-hidden="true" />
      <span className="min-w-0">
        Viewing <span className="font-semibold">{viewing.name}</span> as platform staff. This access
        is recorded.
      </span>
      <Button
        size="sm"
        variant="outline"
        className="ml-auto shrink-0"
        onClick={stopViewing}
      >
        Back to {homeOrgName}
      </Button>
    </div>
  );
}
