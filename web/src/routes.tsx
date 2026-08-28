import { lazy, Suspense } from "react";
import { createBrowserRouter, Navigate, Outlet, useRouteError } from "react-router";
import { Skeleton } from "@/components/ui/skeleton";
import { AppShell } from "@/components/layout/AppShell";
import { AuthShell } from "@/components/layout/AuthShell";
import { RouteErrorState } from "@/components/shared/ErrorBoundary";
import { useAuth } from "@/lib/auth";

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
const BillingPage = lazy(() => import("@/features/billing/BillingPage"));
const AuditPage = lazy(() => import("@/features/audit/AuditPage"));
const LogsPage = lazy(() => import("@/features/logs/LogsPage"));
const SettingsPage = lazy(() => import("@/features/settings/SettingsPage"));
const FilesPage = lazy(() => import("@/features/files/FilesPage"));
const FlagsPage = lazy(() => import("@/features/flags/FlagsPage"));
const TagsPage = lazy(() => import("@/features/tags/TagsPage"));
const CannedRepliesPage = lazy(() => import("@/features/canned-replies/CannedRepliesPage"));
const SiteAdminPage = lazy(() => import("@/features/site/SiteAdminPage"));
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

function PageLoader() {
  return (
    <div className="flex h-full items-center justify-center p-8">
      <Skeleton className="h-8 w-48" />
    </div>
  );
}

function SuspenseWrap({ children }: { children: React.ReactNode }) {
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

export const router = createBrowserRouter([
  {
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
  },
  {
    element: <ProtectedRoute />,
    errorElement: <RouteErrorBoundary />,
    children: [
      {
        element: <AppShell />,
        children: [
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
          { path: "billing", element: <SuspenseWrap><BillingPage /></SuspenseWrap> },
          { path: "audit", element: <SuspenseWrap><AuditPage /></SuspenseWrap> },
          { path: "logs", element: <SuspenseWrap><LogsPage /></SuspenseWrap> },
          { path: "flags", element: <SuspenseWrap><FlagsPage /></SuspenseWrap> },
          { path: "site", element: <SuspenseWrap><SiteAdminPage /></SuspenseWrap> },
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
        ],
      },
    ],
  },
  { path: "*", element: <Navigate to="/" replace /> },
]);
