import { lazy } from "react";
import { createBrowserRouter, Navigate, type RouteObject } from "react-router";
import {
  PlatformAdminRoute,
  protectedShell,
  publicRoutes,
  SuspenseWrap,
  tenantRoutes,
} from "@/routes-shared";

/**
 * The private admin console, served from admin.prabhixtechnologies.com.
 *
 * <p>Carries the tenant pages, because Prabhix's own inbox, chat and visitors live in its own
 * organization, plus the platform pages that span every organization. Billing is left out: there is
 * no subscription to manage for the company that owns the platform.
 */

/** Declared here rather than in the shared module so the OneOps bundle does not carry it. */
const OpsHubPage = lazy(() => import("@/features/ops/OpsHubPage"));

const platformRoutes: RouteObject[] = [
  {
    element: <PlatformAdminRoute />,
    children: [
      { path: "ops", element: <SuspenseWrap><OpsHubPage /></SuspenseWrap> },
      // The hub lived at /site before it was named; keep old bookmarks working.
      { path: "site", element: <Navigate to="/ops" replace /> },
    ],
  },
];

export const router = createBrowserRouter([
  publicRoutes,
  protectedShell([...tenantRoutes, ...platformRoutes]),
  // Billing is the only path that exists in OneOps and not here; typing it lands on the dashboard.
  { path: "*", element: <Navigate to="/" replace /> },
]);
