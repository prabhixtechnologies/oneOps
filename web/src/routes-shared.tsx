import { lazy, Suspense } from "react";
import { Navigate, Outlet, useRouteError, type RouteObject } from "react-router";
import { Skeleton } from "@/components/ui/skeleton";
import { AppShell } from "@/components/layout/AppShell";
import { AuthShell } from "@/components/layout/AuthShell";
import { RouteErrorState } from "@/components/shared/ErrorBoundary";
import { useAuth } from "@/lib/auth";

/**
 * Everything both console apps share: the pages, the guards, and the sign-in routes.
 *
 * There are two apps built from this source tree — the OneOps product, sold to customers, and the
 * private admin console. They overlap almost entirely, because Prabhix's own day-to-day work
 * happens in the admin app rather than in the customer product. Keeping one copy of the route
 * definitions means a page added here appears in both without anyone remembering to do it twice,
 * while {@link ./routes.tsx} and {@link ./routes-admin.tsx} decide what each app actually mounts.
 */

const LoginPage = lazy(() => import("@/features/auth/LoginPage").then((m) => ({ default: m.LoginPage })));
const SignupPage = lazy(() => import("@/features/auth/SignupPage").then((m) => ({ default: m.SignupPage })));
const ForgotPasswordPage = lazy(() =>
  import("@/features/auth/ForgotPasswordPage").then((m) => ({ default: m.ForgotPasswordPage })),
);
const ResetPasswordPage = lazy(() =>
  import("@/features/auth/ForgotPasswordPage").then((m) => ({ default: m.ResetPasswordPage })),
);
const AcceptInvitePage = lazy(() =>
  import("@/features/auth/AcceptInvitePage").then((m) => ({ default: m.AcceptInvitePage })),
);
const MagicLinkPage = lazy(() =>
  import("@/features/auth/MagicLinkPage").then((m) => ({ default: m.MagicLinkPage })),
);
const ChatPage = lazy(() => import("@/features/chat/ChatPage"));
const ChatSettingsPage = lazy(() => import("@/features/chat/ChatSettingsPage"));
const VisitorsPage = lazy(() => import("@/features/visitors/VisitorsPage"));
const VisitorDetailPage = lazy(() => import("@/features/visitors/VisitorDetailPage"));
const DashboardPage = lazy(() => import("@/features/dashboard/DashboardPage"));
const InboxPage = lazy(() => import("@/features/inbox/InboxPage"));
const MailboxesPage = lazy(() => import("@/features/mailboxes/MailboxesPage"));
const MailboxDetailPage = lazy(() => import("@/features/mailboxes/MailboxDetailPage"));
const DomainsPage = lazy(() => import("@/features/maildomains/DomainsPage"));
const DomainDnsPage = lazy(() =>
  import("@/features/maildomains/DomainsPage").then((m) => ({ default: m.DomainDnsPage })),
);
const TemplatesPage = lazy(() => import("@/features/templates/TemplatesPage"));
const TemplateEditorPage = lazy(() =>
  import("@/features/templates/TemplatesPage").then((m) => ({ default: m.TemplateEditorPage })),
);
const MembersPage = lazy(() => import("@/features/members/MembersPage"));
const AuditPage = lazy(() => import("@/features/audit/AuditPage"));
const LogsPage = lazy(() => import("@/features/logs/LogsPage"));
const SettingsPage = lazy(() => import("@/features/settings/SettingsPage"));
const FilesPage = lazy(() => import("@/features/files/FilesPage"));
const FlagsPage = lazy(() => import("@/features/flags/FlagsPage"));
const TagsPage = lazy(() => import("@/features/tags/TagsPage"));
const CannedRepliesPage = lazy(() => import("@/features/canned-replies/CannedRepliesPage"));
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

export function PageLoader() {
  return (
    <div className="flex h-full items-center justify-center p-8">
      <Skeleton className="h-8 w-48" />
    </div>
  );
}

export function SuspenseWrap({ children }: { children: React.ReactNode }) {
  return <Suspense fallback={<PageLoader />}>{children}</Suspense>;
}

function RouteErrorBoundary() {
  const error = useRouteError();
  const message = error instanceof Error ? error.message : "Unknown error";
  return <RouteErrorState error={new Error(message)} />;
}

function ProtectedRoute() {
  const { isAuthenticated, isLoading } = useAuth();
  if (isLoading) return <PageLoader />;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return <Outlet />;
}

function PublicRoute() {
  const { isAuthenticated, isLoading } = useAuth();
  if (isLoading) return <PageLoader />;
  if (isAuthenticated) return <Navigate to="/" replace />;
  return <Outlet />;
}

/**
 * Staff-only routes. The sidebar already hides these links, but hiding a link is not access
 * control: the path was reachable by typing it. The server rejects the underlying calls either
 * way, so this only decides whether a non-admin sees an empty page or gets sent home.
 *
 * <p>Still applied in the admin app, where the whole point is that only platform admins belong.
 * Being served from admin.prabhixtechnologies.com proves nothing about who is signed in — the same
 * session cookie works on both hostnames.
 */
export function PlatformAdminRoute() {
  const { me, isLoading } = useAuth();
  if (isLoading) return <PageLoader />;
  if (!me?.platformAdmin) return <Navigate to="/" replace />;
  return <Outlet />;
}

/** Sign-in, sign-up and the links that arrive by email. Identical in both apps. */
export const publicRoutes: RouteObject = {
  element: <PublicRoute />,
  errorElement: <RouteErrorBoundary />,
  children: [
    {
      element: <AuthShell />,
      children: [
        { path: "/login", element: <SuspenseWrap><LoginPage /></SuspenseWrap> },
        { path: "/signup", element: <SuspenseWrap><SignupPage /></SuspenseWrap> },
        { path: "/forgot-password", element: <SuspenseWrap><ForgotPasswordPage /></SuspenseWrap> },
        { path: "/reset-password", element: <SuspenseWrap><ResetPasswordPage /></SuspenseWrap> },
        { path: "/magic-link", element: <SuspenseWrap><MagicLinkPage /></SuspenseWrap> },
        { path: "/invite/:token", element: <SuspenseWrap><AcceptInvitePage /></SuspenseWrap> },
      ],
    },
  ],
};

/**
 * The pages that operate on one organization's data: inbox, chat, visitors, shop, settings.
 *
 * <p>Present in both apps on purpose. In OneOps they are the product; in the admin app they are how
 * Prabhix runs its own organization, which is what makes it possible never to open the customer
 * product to do everyday work.
 */
export const tenantRoutes: RouteObject[] = [
  { index: true, element: <SuspenseWrap><DashboardPage /></SuspenseWrap> },
  { path: "chat", element: <SuspenseWrap><ChatPage /></SuspenseWrap> },
  { path: "chat/settings", element: <SuspenseWrap><ChatSettingsPage /></SuspenseWrap> },
  { path: "visitors", element: <SuspenseWrap><VisitorsPage /></SuspenseWrap> },
  { path: "visitors/:id", element: <SuspenseWrap><VisitorDetailPage /></SuspenseWrap> },
  { path: "inbox", element: <SuspenseWrap><InboxPage /></SuspenseWrap> },
  { path: "mailboxes", element: <SuspenseWrap><MailboxesPage /></SuspenseWrap> },
  { path: "mailboxes/:id", element: <SuspenseWrap><MailboxDetailPage /></SuspenseWrap> },
  { path: "domains", element: <SuspenseWrap><DomainsPage /></SuspenseWrap> },
  { path: "domains/:id", element: <SuspenseWrap><DomainDnsPage /></SuspenseWrap> },
  { path: "templates", element: <SuspenseWrap><TemplatesPage /></SuspenseWrap> },
  { path: "templates/:key", element: <SuspenseWrap><TemplateEditorPage /></SuspenseWrap> },
  { path: "tags", element: <SuspenseWrap><TagsPage /></SuspenseWrap> },
  { path: "canned-replies", element: <SuspenseWrap><CannedRepliesPage /></SuspenseWrap> },
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

/**
 * Wraps a set of in-app routes in the authenticated shell.
 *
 * <p>Routes that belong to only one app — Billing in OneOps, the Ops Hub in admin — are declared in
 * that app's own route file rather than here. Declaring `lazy(() => import(...))` in this shared
 * module would emit the chunk into both bundles even where nothing routes to it, since Rollup cannot
 * prove the call is free of side effects. Keeping them apart is what makes "the OneOps build does
 * not contain the platform pages" true rather than merely intended.
 */
export function protectedShell(children: RouteObject[]): RouteObject {
  return {
    element: <ProtectedRoute />,
    errorElement: <RouteErrorBoundary />,
    children: [{ element: <AppShell />, children }],
  };
}
