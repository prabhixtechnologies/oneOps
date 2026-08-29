import {
  Activity,
  BarChart3,
  Briefcase,
  CreditCard,
  FileText,
  Flag,
  FolderOpen,
  Globe,
  Inbox,
  LayoutDashboard,
  Mail,
  MessageSquare,
  Percent,
  Receipt,
  ScrollText,
  Settings,
  ShoppingBag,
  Sparkles,
  Store,
  Tag,
  Users,
  type LucideIcon,
} from "lucide-react";
import { PERMISSIONS, type Permission } from "@/lib/permissions";
import type { AppMode } from "@/lib/app-mode";

export interface NavItem {
  to: string;
  icon: LucideIcon;
  label: string;
  /** Hides the link unless the signed-in member holds this permission. */
  permission?: Permission;
  /** Hides the link unless the signed-in user is a platform admin. */
  platformAdminOnly?: boolean;
}

export interface NavGroup {
  heading: string;
  items: NavItem[];
}

/**
 * Groups shared by both consoles, in the order they appear.
 *
 * <p>Headings exist to make the admin console's boundary legible: everything above "Platform" acts
 * on one organization, everything under it spans all of them. Without that line drawn visibly, the
 * two consoles look like the same app and it stops being obvious whose data is on screen.
 */
const tenantGroups: NavGroup[] = [
  {
    heading: "Overview",
    items: [{ to: "/", icon: LayoutDashboard, label: "Dashboard" }],
  },
  {
    heading: "Conversations",
    items: [
      { to: "/chat", icon: MessageSquare, label: "Live Chat", permission: PERMISSIONS.CHAT_READ },
      { to: "/visitors", icon: Activity, label: "Visitors", permission: PERMISSIONS.VISITOR_READ },
      { to: "/inbox", icon: Inbox, label: "Shared Inbox" },
    ],
  },
  {
    heading: "Mail",
    items: [
      { to: "/mailboxes", icon: Mail, label: "Mailboxes", permission: PERMISSIONS.MAIL_MAILBOX_READ },
      { to: "/domains", icon: Globe, label: "Mail Domains", permission: PERMISSIONS.MAIL_DOMAIN_READ },
      { to: "/templates", icon: FileText, label: "Templates", permission: PERMISSIONS.MAIL_TEMPLATE_READ },
      { to: "/tags", icon: Tag, label: "Mail tags", permission: PERMISSIONS.MAIL_READ },
      { to: "/canned-replies", icon: MessageSquare, label: "Canned replies", permission: PERMISSIONS.MAIL_READ },
    ],
  },
  {
    heading: "Shop",
    items: [
      { to: "/commerce", icon: Store, label: "Shop dashboard", permission: PERMISSIONS.COMMERCE_ORDER_READ },
      { to: "/commerce/products", icon: ShoppingBag, label: "Products", permission: PERMISSIONS.COMMERCE_CATALOG_READ },
      { to: "/commerce/orders", icon: Receipt, label: "Orders", permission: PERMISSIONS.COMMERCE_ORDER_READ },
      { to: "/commerce/customers", icon: Users, label: "Customers", permission: PERMISSIONS.COMMERCE_CUSTOMER_READ },
      { to: "/commerce/discounts", icon: Percent, label: "Discounts", permission: PERMISSIONS.COMMERCE_DISCOUNT_MANAGE },
      { to: "/commerce/settings", icon: Store, label: "Shop settings", permission: PERMISSIONS.COMMERCE_SETTINGS_MANAGE },
    ],
  },
  {
    heading: "Workspace",
    items: [
      { to: "/files", icon: FolderOpen, label: "Files", permission: PERMISSIONS.FILE_READ },
      { to: "/members", icon: Users, label: "Members & Roles", permission: PERMISSIONS.ORG_MEMBER_READ },
      { to: "/ai/settings", icon: Sparkles, label: "AI settings", permission: PERMISSIONS.AI_CONFIGURE },
      { to: "/ai/usage", icon: BarChart3, label: "AI usage", permission: PERMISSIONS.AI_USAGE_READ },
      { to: "/audit", icon: FileText, label: "Audit Log", permission: PERMISSIONS.AUDIT_READ },
      { to: "/logs", icon: ScrollText, label: "Event Logs", permission: PERMISSIONS.LOG_READ },
      { to: "/flags", icon: Flag, label: "Feature flags" },
      { to: "/settings", icon: Settings, label: "Settings" },
    ],
  },
];

/** Appended to the Workspace group in OneOps only; Prabhix does not bill itself. */
const billingItem: NavItem = {
  to: "/billing",
  icon: CreditCard,
  label: "Billing",
  permission: PERMISSIONS.BILLING_READ,
};

/** Admin-only, and the reason the admin console exists as a separate app. */
const platformGroup: NavGroup = {
  heading: "Platform",
  items: [{ to: "/ops", icon: Briefcase, label: "Ops Hub", platformAdminOnly: true }],
};

function withBilling(groups: NavGroup[]): NavGroup[] {
  return groups.map((group) =>
    group.heading === "Workspace"
      ? // Before Settings, which reads as the last entry in a list.
        { ...group, items: [...group.items.slice(0, -1), billingItem, ...group.items.slice(-1)] }
      : group,
  );
}

export function navGroupsFor(mode: AppMode): NavGroup[] {
  return mode === "admin" ? [...tenantGroups, platformGroup] : withBilling(tenantGroups);
}
