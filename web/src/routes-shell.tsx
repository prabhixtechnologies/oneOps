import { lazy, Suspense } from "react";
import { Navigate, Outlet, useRouteError, type RouteObject } from "react-router";
import { Skeleton } from "@/components/ui/skeleton";
import { AppShell } from "@/components/layout/AppShell";
import { AuthShell } from "@/components/layout/AuthShell";
import { RouteErrorState } from "@/components/shared/ErrorBoundary";
import { useAuth } from "@/lib/auth";

/**
 * The routing plumbing both apps need: the guards, the authenticated shell, and sign-in.
 *
 * <h2>Why the tenant pages are not here</h2>
 *
 * <p>They used to be, and it quietly undid the split. A `lazy(() => import(...))` at module scope
 * emits that chunk into any bundle importing this file, whether or not a route reaches it — Rollup
 * cannot prove the dynamic import is free of side effects, so it cannot drop it. The admin console
 * imports this module for `protectedShell` alone, and was therefore shipping thirty chunks of chat,
 * mail and commerce it never routes to.
 *
 * <p>So the tenant pages live in {@link ./routes-tenant.tsx}, which only {@link ./routes.tsx}
 * imports. Anything added here is added to both apps; that is the test for whether it belongs.
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
const VerifyEmailPage = lazy(() =>
  import("@/features/auth/VerifyEmailPage").then((m) => ({ default: m.VerifyEmailPage })),
);

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
 * Routes that have to work whether or not somebody is signed in.
 *
 * <p>Separate from {@link publicRoutes} because that group redirects an authenticated visitor to `/`,
 * which is right for a sign-in form and wrong for a link that arrives by email. Email verification is
 * usually requested *from* the settings page, so the person clicking the link is already signed in;
 * under the public guard they would be bounced before the token was consumed and the address would
 * never be verified.
 */
export const unguardedRoutes: RouteObject = {
  errorElement: <RouteErrorBoundary />,
  children: [
    {
      element: <AuthShell />,
      children: [
        { path: "/verify-email", element: <SuspenseWrap><VerifyEmailPage /></SuspenseWrap> },
      ],
    },
  ],
};

/** Wraps a set of in-app routes in the authenticated shell. */
export function protectedShell(children: RouteObject[]): RouteObject {
  return {
    element: <ProtectedRoute />,
    errorElement: <RouteErrorBoundary />,
    children: [{ element: <AppShell />, children }],
  };
}
