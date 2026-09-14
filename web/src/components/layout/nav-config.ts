import {
  Activity,
  Briefcase,
  Building2,
  Cloud,
  CreditCard,
  FileText,
  Flag,
  FolderOpen,
  Inbox,
  KeyRound,
  LayoutDashboard,
  Mail,
  MessageSquare,
  Percent,
  Receipt,
  ScrollText,
  Settings,
  Shield,
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
  /** Hides the link unless the signed-in member holds at least one of these. */
  anyPermission?: Permission[];
  /** Hides the link unless the signed-in user is a platform admin. */
  platformAdminOnly?: boolean;
  /**
   * Platform staff roles that may see this link. OWNER always sees every item. Unset means any
   * platform admin. The server still gates the API.
   */
  staffRoles?: readonly string[];
  /** Exact path match. Defaults to true only for `/`; Inbox stays active on `/inbox/:id`. */
  end?: boolean;
}

export interface NavGroup {
  heading: string;
  items: NavItem[];
  /** Renders as a nested heading under Settings (Advanced). */
  nested?: boolean;
}

/**
 * The pages that act on a single organization's data — the OneOps product.
 *
 * <p>The admin console has none of these. They act on one organization, and its subject is the
 * platform; staff needing a customer's inbox are handed off to OneOps instead.
 *
 * <p>OneOps is the operations console for a small online business: website visitors and live chat, a
 * shared helpdesk inbox, storefront and orders, team and billing.
 */
const tenantGroups: NavGroup[] = [
  {
    heading: "Overview",
    items: [{ to: "/", icon: LayoutDashboard, label: "Overview" }],
  },
  {
    heading: "Conversations",
    items: [
      { to: "/inbox", icon: Inbox, label: "Inbox", permission: PERMISSIONS.MAIL_READ },
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
    heading: "Team",
    items: [
      { to: "/members", icon: Users, label: "Members", permission: PERMISSIONS.ORG_MEMBER_READ },
      { to: "/billing", icon: CreditCard, label: "Billing", permission: PERMISSIONS.BILLING_READ },
    ],
  },
  {
    heading: "Settings",
    items: [
      { to: "/settings", icon: Settings, label: "Org", end: true },
      { to: "/settings/mail", icon: Mail, label: "Mail", anyPermission: [
        PERMISSIONS.MAIL_MAILBOX_MANAGE,
        PERMISSIONS.MAIL_THREAD_UPDATE,
        PERMISSIONS.MAIL_DOMAIN_READ,
      ] },
      { to: "/ai/settings", icon: Sparkles, label: "AI", permission: PERMISSIONS.AI_CONFIGURE },
      { to: "/settings/api-keys", icon: KeyRound, label: "API keys", permission: PERMISSIONS.ORG_API_KEY_MANAGE, end: true },
    ],
  },
  {
    heading: "Advanced",
    nested: true,
    items: [
      { to: "/flags", icon: Flag, label: "Flags" },
      { to: "/logs", icon: ScrollText, label: "Event logs", permission: PERMISSIONS.LOG_READ },
      { to: "/audit", icon: FileText, label: "Audit", permission: PERMISSIONS.AUDIT_READ },
      { to: "/files", icon: FolderOpen, label: "Files", permission: PERMISSIONS.FILE_READ },
    ],
  },
];

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
      { to: "/", icon: Briefcase, label: "Overview", platformAdminOnly: true },
      { to: "/tenants", icon: Building2, label: "Tenants and shops", platformAdminOnly: true, staffRoles: ["SUPPORT"] },
      { to: "/identity", icon: Shield, label: "Identity", platformAdminOnly: true, staffRoles: ["SECURITY"] },
      { to: "/revenue", icon: CreditCard, label: "Revenue", platformAdminOnly: true, staffRoles: ["BILLING"] },
      { to: "/infra", icon: Cloud, label: "Infra", platformAdminOnly: true, staffRoles: ["OPERATOR"] },
      { to: "/mail", icon: Mail, label: "Mail health", platformAdminOnly: true, staffRoles: ["SUPPORT"] },
      { to: "/staff", icon: Users, label: "Staff", platformAdminOnly: true, staffRoles: ["OWNER"] },
      { to: "/mobistack", icon: Store, label: "MobiStack ops", platformAdminOnly: true, staffRoles: ["SUPPORT"] },
      { to: "/commons", icon: Flag, label: "Commons review", platformAdminOnly: true, staffRoles: ["SUPPORT"] },
      { to: "/logs", icon: ScrollText, label: "Event logs", platformAdminOnly: true },
    ],
  },
];

/**
 * The navigation for this build, decided at module scope: the answer cannot change while the app is
 * running, and this way the branch not taken is dropped from the bundle along with every string in
 * the groups it names.
 */
export const navGroups: NavGroup[] = IS_ADMIN_APP ? platformGroups : tenantGroups;
