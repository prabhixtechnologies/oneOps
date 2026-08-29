/**
 * The organization a platform admin is currently looking at, when it is not their own.
 *
 * <p>Support work means opening a customer's inbox to see what they see. The server already allows
 * this — staff can name any organization in the `X-Prabhix-Org` header, membership or not — but the
 * console had no way to use it. The org switcher goes through `/organizations/{id}/select`, which
 * requires membership and so refuses every customer organization.
 *
 * <p>So this sits alongside the session's own organization rather than replacing it: the signed-in
 * identity never changes, only which tenant's rows the requests ask for. Leaving it is immediate and
 * cannot fail, because it does not involve the server.
 *
 * <h2>Why per-tab</h2>
 *
 * <p>Kept in `sessionStorage`, so one tab can be inside a customer's data while another shows your
 * own — which is what you want when comparing the two. It also means closing the tab ends it, rather
 * than leaving a forgotten impersonation to greet you tomorrow.
 *
 * <h2>Auditing</h2>
 *
 * <p>Every request made while this is set is recorded server-side against the organization being
 * viewed. Clearing it here does not erase that, by design.
 */

const STORAGE_KEY = "prabhix_viewing_org";

export interface ViewingOrg {
  id: string;
  name: string;
}

type Listener = (org: ViewingOrg | null) => void;

const listeners = new Set<Listener>();
let current: ViewingOrg | null = read();

function read(): ViewingOrg | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed: unknown = JSON.parse(raw);
    if (
      typeof parsed === "object" &&
      parsed !== null &&
      typeof (parsed as ViewingOrg).id === "string" &&
      typeof (parsed as ViewingOrg).name === "string"
    ) {
      return parsed as ViewingOrg;
    }
    return null;
  } catch {
    // Private browsing modes can throw on access rather than returning null.
    return null;
  }
}

function write(org: ViewingOrg | null) {
  try {
    if (org) sessionStorage.setItem(STORAGE_KEY, JSON.stringify(org));
    else sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // Losing this across a reload is a small annoyance; failing the click is not acceptable.
  }
}

/** The organization being viewed, or null when looking at your own. */
export function viewingOrg(): ViewingOrg | null {
  return current;
}

/**
 * Called by the API client for every request, so a change takes effect on the next call without
 * anything having to be re-registered.
 */
export function viewingOrgId(): string | null {
  return current?.id ?? null;
}

export function setViewingOrg(org: ViewingOrg | null) {
  if (current?.id === org?.id) return;
  current = org;
  write(org);
  listeners.forEach((listener) => listener(org));
}

/** Subscribe to changes. Returns the unsubscribe function. */
export function subscribeToViewingOrg(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
