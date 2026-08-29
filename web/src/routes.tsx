import { lazy } from "react";
import { createBrowserRouter, Navigate, type RouteObject } from "react-router";
import { protectedShell, publicRoutes, SuspenseWrap, unguardedRoutes } from "@/routes-shell";
import { tenantRoutes } from "@/routes-tenant";

/**
 * The OneOps product, served from oneops.prabhixtechnologies.com.
 *
 * <p>Everything an organization needs, and nothing that reaches across organizations. The platform
 * pages are not merely hidden here — they are not in this bundle at all, so no amount of URL
 * guessing loads the code.
 *
 * <p>This is also where Prabhix's own team works: the company is a customer of its own product, and
 * its inbox, chat and shop are this app pointed at the Prabhix organization. Platform staff sent
 * here from the admin console to support a customer land in the same pages, with the organization
 * carried across — see {@link ./lib/staff-handoff.ts}.
 */

/** Declared here rather than in a shared module so the admin bundle does not carry it. */
const BillingPage = lazy(() => import("@/features/billing/BillingPage"));

const billingRoutes: RouteObject[] = [
  { path: "billing", element: <SuspenseWrap><BillingPage /></SuspenseWrap> },
];

export const router = createBrowserRouter([
  publicRoutes,
  unguardedRoutes,
  protectedShell([...tenantRoutes, ...billingRoutes]),
  { path: "*", element: <Navigate to="/" replace /> },
]);
