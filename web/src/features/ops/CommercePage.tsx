import { Navigate } from "react-router";

/** Commerce moved: shops live under Tenants, revenue under /revenue. */
export default function CommercePage() {
  return <Navigate to="/revenue" replace />;
}
