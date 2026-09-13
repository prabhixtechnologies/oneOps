import {
  Activity,
  BarChart3,
  Briefcase,
  Cloud,
  CreditCard,
  FileText,
  Flag,
  FolderOpen,
  LayoutDashboard,
  MessageSquare,
  Percent,
  Receipt,
  ScrollText,
  Settings,
  ShoppingBag,
  Sparkles,
  Store,
  Users,
  type LucideIcon,
} from "lucide-react";
import { PERMISSIONS, type Permission } from "@/lib/permissions";
import { IS_ADMIN_APP } from "@/lib/app-mode";

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
 * The pages that act on a single organization's data — the OneOps product.
 *
 * <p>The admin console has none of these. They act on one organization, and its subject is the
 * platform; staff needing a customer's inbox are handed off to OneOps instead.
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

/**
 * The whole of the admin console: the surfaces that span every organization.
 *
 * <p>Short by design. Everything a customer's staff do day to day is a tenant page and lives in
 * OneOps; what is left here is the platform itself — who the customers are, what the marketing
 * pipeline is doing, and what the system has been logging across all of them.
 */
const platformGroups: NavGroup[] = [
  {
    heading: "Platform",
    items: [
      { to: "/", icon: Briefcase, label: "Ops Hub", platformAdminOnly: true },
      { to: "/commerce", icon: CreditCard, label: "Commerce", platformAdminOnly: true },
      { to: "/infra", icon: Cloud, label: "Infra", platformAdminOnly: true },
      { to: "/logs", icon: ScrollText, label: "Event Logs", platformAdminOnly: true },
    ],
  },
];

function withBilling(groups: NavGroup[]): NavGroup[] {
  return groups.map((group) =>
    group.heading === "Workspace"
      ? // Before Settings, which reads as the last entry in a list.
        { ...group, items: [...group.items.slice(0, -1), billingItem, ...group.items.slice(-1)] }
      : group,
  );
}

/**
 * The navigation for this build, decided at module scope: the answer cannot change while the app is
 * running, and this way the branch not taken is dropped from the bundle along with every string in
 * the groups it names.
 */
export const navGroups: NavGroup[] = IS_ADMIN_APP ? platformGroups : withBilling(tenantGroups);
