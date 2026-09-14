import { lazy } from "react";
import { createBrowserRouter, Navigate, type RouteObject } from "react-router";
import {
  PlatformAdminRoute,
  protectedShell,
  publicRoutes,
  SuspenseWrap,
  unguardedRoutes,
} from "@/routes-shell";

/**
 * The private admin console, served from admin.prabhixtechnologies.com.
 *
 * <p>A control tower, not a copy of the product. It mounts the platform surfaces and nothing else:
 * the tenant directory, identity, revenue, infra, mail, staff, MobiStack ops, commons review, and
 * platform-wide event logs. There is no inbox, no chat, no shop and no settings here, because every
 * one of those acts on a single organization and this console's subject is the platform.
 *
 * <h2>Where the tenant pages went</h2>
 *
 * <p>They are in OneOps, which is where they belong. Prabhix runs its own business as a customer of
 * its own product — signed in at oneops.prabhixtechnologies.com like anybody else — and staff who
 * need to see a customer's data are handed off to the same place, carrying the organization with
 * them. See {@link ./features/ops/TenantsTab.tsx}.
 *
 * <p>The earlier arrangement mounted the whole customer product here and revealed it once an
 * organization was chosen. It was correct about access and wrong about identity: the two consoles
 * were the same 22 feature directories with one page's difference, so nothing on screen told you
 * which app you had opened.
 */

/** Declared here rather than in the shared module so the OneOps bundle does not carry them. */
const OpsHubPage = lazy(() => import("@/features/ops/OpsHubPage"));
const LogsPage = lazy(() => import("@/features/logs/LogsPage"));
const TenantsPage = lazy(() => import("@/features/ops/TenantsPage"));
const IdentityPage = lazy(() => import("@/features/ops/IdentityPage"));
const RevenuePage = lazy(() => import("@/features/ops/RevenuePage"));
const InfraPage = lazy(() => import("@/features/ops/InfraPage"));
const MailHealthPage = lazy(() => import("@/features/ops/MailHealthPage"));
const StaffPage = lazy(() => import("@/features/ops/StaffPage"));
const MobiStackOpsPage = lazy(() => import("@/features/ops/MobiStackOpsPage"));
const CommonsReviewPage = lazy(() => import("@/features/ops/CommonsReviewPage"));

const platformRoutes: RouteObject[] = [
  {
    element: <PlatformAdminRoute />,
    children: [
      { index: true, element: <SuspenseWrap><OpsHubPage /></SuspenseWrap> },
      { path: "ops", element: <Navigate to="/" replace /> },
      { path: "site", element: <Navigate to="/" replace /> },
      { path: "tenants", element: <SuspenseWrap><TenantsPage /></SuspenseWrap> },
      { path: "identity", element: <SuspenseWrap><IdentityPage /></SuspenseWrap> },
      { path: "revenue", element: <SuspenseWrap><RevenuePage /></SuspenseWrap> },
      { path: "commerce", element: <Navigate to="/revenue" replace /> },
      { path: "infra", element: <SuspenseWrap><InfraPage /></SuspenseWrap> },
      { path: "mail", element: <SuspenseWrap><MailHealthPage /></SuspenseWrap> },
      { path: "staff", element: <SuspenseWrap><StaffPage /></SuspenseWrap> },
      { path: "mobistack", element: <SuspenseWrap><MobiStackOpsPage /></SuspenseWrap> },
      { path: "commons", element: <SuspenseWrap><CommonsReviewPage /></SuspenseWrap> },
      { path: "logs", element: <SuspenseWrap><LogsPage /></SuspenseWrap> },
    ],
  },
];

export const router = createBrowserRouter([
  publicRoutes,
  unguardedRoutes,
  protectedShell(platformRoutes),
  // Every tenant path — /chat, /commerce/orders, /settings — lands here. Those URLs exist in
  // OneOps, and a staff bookmark to one of them should not resolve to a blank screen.
  { path: "*", element: <Navigate to="/" replace /> },
]);
