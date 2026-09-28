import { useMemo } from "react";
import { useAuth } from "@/lib/auth";
import { hasAnyPermission, hasPermission } from "@/lib/permissions";
import { useStaffRoles } from "@/features/ops/api";
import { navGroups, type NavGroup, type NavItem } from "./nav-config";

/**
 * The navigation this person can actually reach.
 *
 * <p>The rule used to live inside the sidebar's render, which meant the command palette could not
 * use it and kept its own hand-written copy of the page list instead. The two had already drifted -
 * the palette offered pages the sidebar had stopped showing, and offered them to people without the
 * permission to open them, so choosing one was a trip to a 403. There is one list now, and one
 * answer to who may see a given entry.
 */
export function useVisibleNav(): NavGroup[] {
  const { me, permissions } = useAuth();
  const staff = useStaffRoles();
  const roles = staff.data?.roles;
  const platformAdmin = me?.platformAdmin ?? false;
  // Joined, so a query that re-resolves to the same roles does not recompute the whole tree.
  const roleKey = roles?.join(",") ?? "";

  return useMemo(() => {
    const visible = (item: NavItem) => {
      if (item.platformAdminOnly && !platformAdmin) return false;
      if (item.staffRoles && staff.isSuccess) {
        const held = roleKey ? roleKey.split(",") : [];
        // OWNER sees everything; otherwise one matching role is enough.
        if (!held.includes("OWNER") && !item.staffRoles.some((role) => held.includes(role))) {
          return false;
        }
      }
      if (item.anyPermission && !hasAnyPermission(permissions, item.anyPermission)) return false;
      if (item.permission && !hasPermission(permissions, item.permission)) return false;
      return true;
    };

    return navGroups
      .map((group) => ({ ...group, items: group.items.filter(visible) }))
      // A group whose every link is hidden would otherwise leave a stray heading behind.
      .filter((group) => group.items.length > 0);
  }, [platformAdmin, permissions, roleKey, staff.isSuccess]);
}
