import { lazy } from "react";
import { createBrowserRouter, Navigate, type RouteObject } from "react-router";
import { protectedShell, publicRoutes, SuspenseWrap, tenantRoutes } from "@/routes-shared";

/**
 * The OneOps product, served from oneops.prabhixtechnologies.com.
 *
 * <p>Everything a customer's organization needs, and nothing that reaches across organizations. The
 * platform pages are not merely hidden here — they are not in this bundle at all, so no amount of
 * URL guessing loads the code.
 */

/** Declared here rather than in the shared module so the admin bundle does not carry it. */
const BillingPage = lazy(() => import("@/features/billing/BillingPage"));

const billingRoutes: RouteObject[] = [
  { path: "billing", element: <SuspenseWrap><BillingPage /></SuspenseWrap> },
];

export const router = createBrowserRouter([
  publicRoutes,
  protectedShell([...tenantRoutes, ...billingRoutes]),
  { path: "*", element: <Navigate to="/" replace /> },
]);
