/**
 * Sending platform staff from the admin console into a customer's data.
 *
 * <h2>Why this is a handoff and not a page</h2>
 *
 * <p>There is no admin API for a customer's mail, chat or orders. The `/admin/platform` endpoints
 * return counts and a tenant directory — deliberately, so an operator can judge the platform's
 * health without reading anybody's mail. The only way to read a customer's operational data is the
 * ordinary tenant endpoints with `X-Prabhix-Org` naming their organization, which the server permits
 * for platform admins and records.
 *
 * <p>Which means seeing a customer's inbox requires the inbox — the real one, from the product. The
 * admin console could mount its own copy, and did: that is how it ended up containing all thirty
 * tenant pages and being indistinguishable from OneOps. Handing off instead keeps admin a control
 * tower, and has a second benefit that matters more than the bundle size — support staff look at
 * exactly the screen the customer is describing, not a second implementation of it that drifts.
 *
 * <h2>How the organization travels</h2>
 *
 * <p>In the URL, because it cannot travel any other way: the selection lives in `sessionStorage`,
 * which is per-origin, so admin.prabhixtechnologies.com cannot write the one that
 * oneops.prabhixtechnologies.com reads. The session itself needs no transfer — one cookie covers
 * both hostnames.
 *
 * <p>The parameter is a request, not a grant. OneOps honours it only after the server has confirmed
 * the caller is a platform admin, and the server independently refuses the header from anyone else
 * with `CROSS_TENANT_ACCESS`. A customer typing `?viewAs=<some-uuid>` at their own console gets
 * nothing.
 */

/** Query parameter carrying the organization to open. Read by {@link consumeStaffHandoff}. */
export const HANDOFF_PARAM = "viewAs";

/**
 * Where OneOps lives, as seen from the admin console.
 *
 * <p>Falls back to the production host rather than throwing. A missing build variable should not
 * turn "View as" into a dead button in a console that is otherwise working, and getting it wrong in
 * development is visible immediately.
 */
const ONEOPS_URL = (
  import.meta.env.VITE_ONEOPS_URL ?? "https://oneops.prabhixtechnologies.com"
).replace(/\/+$/, "");

/**
 * The address of a customer's workspace in OneOps.
 *
 * @param organizationId the customer to open
 * @param path where to land, defaulting to the dashboard
 */
export function staffHandoffUrl(organizationId: string, path = "/"): string {
  const url = new URL(path, `${ONEOPS_URL}/`);
  url.searchParams.set(HANDOFF_PARAM, organizationId);
  return url.toString();
}
