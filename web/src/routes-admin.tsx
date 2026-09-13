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
 * the tenant directory, the marketing pipeline, and platform-wide event logs. There is no inbox, no
 * chat, no shop and no settings here, because every one of those acts on a single organization and
 * this console's subject is the platform.
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
const CommercePage = lazy(() => import("@/features/ops/CommercePage"));
const InfraPage = lazy(() => import("@/features/ops/InfraPage"));

const platformRoutes: RouteObject[] = [
  {
    element: <PlatformAdminRoute />,
    children: [
      { index: true, element: <SuspenseWrap><OpsHubPage /></SuspenseWrap> },
      { path: "ops", element: <Navigate to="/" replace /> },
      { path: "site", element: <Navigate to="/" replace /> },
      { path: "commerce", element: <SuspenseWrap><CommercePage /></SuspenseWrap> },
      { path: "infra", element: <SuspenseWrap><InfraPage /></SuspenseWrap> },
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
