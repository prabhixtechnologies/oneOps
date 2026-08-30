import { lazy } from "react";
import { type RouteObject } from "react-router";
import { SuspenseWrap } from "@/routes-shell";

/**
 * The pages that operate on one organization's data: chat, visitors, shop, settings.
 *
 * <p>Imported only by {@link ./routes.tsx}, the OneOps product. Every page here needs an
 * organization to be meaningful, and OneOps always has exactly one — the account's own, or a
 * customer's while platform staff are handed off into it for support.
 *
 * <p>Keeping the `lazy` calls in this file rather than alongside the guards is what keeps them out
 * of the admin bundle: a module-scope dynamic import is emitted into every bundle that imports the
 * file containing it, so a shared module would put all thirty chunks in both apps.
 */

const ChatPage = lazy(() => import("@/features/chat/ChatPage"));
const ChatSettingsPage = lazy(() => import("@/features/chat/ChatSettingsPage"));
const VisitorsPage = lazy(() => import("@/features/visitors/VisitorsPage"));
const VisitorDetailPage = lazy(() => import("@/features/visitors/VisitorDetailPage"));
const DashboardPage = lazy(() => import("@/features/dashboard/DashboardPage"));
const MembersPage = lazy(() => import("@/features/members/MembersPage"));
const AuditPage = lazy(() => import("@/features/audit/AuditPage"));
const LogsPage = lazy(() => import("@/features/logs/LogsPage"));
const SettingsPage = lazy(() => import("@/features/settings/SettingsPage"));
const FilesPage = lazy(() => import("@/features/files/FilesPage"));
const FlagsPage = lazy(() => import("@/features/flags/FlagsPage"));
const CommerceDashboardPage = lazy(() => import("@/features/commerce/CommerceDashboardPage"));
const CommerceProductsPage = lazy(() => import("@/features/commerce/CommerceProductsPage"));
const ProductEditPage = lazy(() => import("@/features/commerce/ProductEditPage"));
const CommerceOrdersPage = lazy(() => import("@/features/commerce/CommerceOrdersPage"));
const CommerceOrderDetailPage = lazy(() => import("@/features/commerce/CommerceOrderDetailPage"));
const CommerceCustomersPage = lazy(() => import("@/features/commerce/CommerceCustomersPage"));
const CommerceCustomerDetailPage = lazy(() => import("@/features/commerce/CommerceCustomerDetailPage"));
const CommerceDiscountsPage = lazy(() => import("@/features/commerce/CommerceDiscountsPage"));
const CommerceSettingsPage = lazy(() => import("@/features/commerce/CommerceSettingsPage"));
const AiSettingsPage = lazy(() => import("@/features/ai/AiSettingsPage"));
const AiUsagePage = lazy(() => import("@/features/ai/AiUsagePage"));

export const tenantRoutes: RouteObject[] = [
  { index: true, element: <SuspenseWrap><DashboardPage /></SuspenseWrap> },
  { path: "chat", element: <SuspenseWrap><ChatPage /></SuspenseWrap> },
  { path: "chat/settings", element: <SuspenseWrap><ChatSettingsPage /></SuspenseWrap> },
  { path: "visitors", element: <SuspenseWrap><VisitorsPage /></SuspenseWrap> },
  { path: "visitors/:id", element: <SuspenseWrap><VisitorDetailPage /></SuspenseWrap> },
  { path: "files", element: <SuspenseWrap><FilesPage /></SuspenseWrap> },
  { path: "members", element: <SuspenseWrap><MembersPage /></SuspenseWrap> },
  { path: "audit", element: <SuspenseWrap><AuditPage /></SuspenseWrap> },
  { path: "logs", element: <SuspenseWrap><LogsPage /></SuspenseWrap> },
  { path: "flags", element: <SuspenseWrap><FlagsPage /></SuspenseWrap> },
  { path: "commerce", element: <SuspenseWrap><CommerceDashboardPage /></SuspenseWrap> },
  { path: "commerce/products", element: <SuspenseWrap><CommerceProductsPage /></SuspenseWrap> },
  { path: "commerce/products/:id", element: <SuspenseWrap><ProductEditPage /></SuspenseWrap> },
  { path: "commerce/orders", element: <SuspenseWrap><CommerceOrdersPage /></SuspenseWrap> },
  { path: "commerce/orders/:id", element: <SuspenseWrap><CommerceOrderDetailPage /></SuspenseWrap> },
  { path: "commerce/customers", element: <SuspenseWrap><CommerceCustomersPage /></SuspenseWrap> },
  { path: "commerce/customers/:id", element: <SuspenseWrap><CommerceCustomerDetailPage /></SuspenseWrap> },
  { path: "commerce/discounts", element: <SuspenseWrap><CommerceDiscountsPage /></SuspenseWrap> },
  { path: "commerce/settings", element: <SuspenseWrap><CommerceSettingsPage /></SuspenseWrap> },
  { path: "ai/settings", element: <SuspenseWrap><AiSettingsPage /></SuspenseWrap> },
  { path: "ai/usage", element: <SuspenseWrap><AiUsagePage /></SuspenseWrap> },
  { path: "settings", element: <SuspenseWrap><SettingsPage /></SuspenseWrap> },
];
