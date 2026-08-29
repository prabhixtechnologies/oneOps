import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * These decide whose data the console shows, so the cases that matter are the ones where it could
 * show the wrong tenant's: a stale value surviving sign-out, or a subscriber missing a change.
 */
describe("impersonation store", () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.resetModules();
  });

  async function load() {
    return import("./impersonation");
  }

  it("reports no override until one is set", async () => {
    const { viewingOrg, viewingOrgId } = await load();
    expect(viewingOrg()).toBeNull();
    expect(viewingOrgId()).toBeNull();
  });

  it("returns the id the API client should send", async () => {
    const { setViewingOrg, viewingOrgId } = await load();
    setViewingOrg({ id: "org-1", name: "Acme" });
    expect(viewingOrgId()).toBe("org-1");
  });

  it("survives a reload, because the banner and the header must agree after one", async () => {
    const first = await load();
    first.setViewingOrg({ id: "org-1", name: "Acme" });

    vi.resetModules();
    const second = await import("./impersonation");
    expect(second.viewingOrg()).toEqual({ id: "org-1", name: "Acme" });
  });

  it("clears, so signing out cannot leave the next person inside a customer", async () => {
    const { setViewingOrg, viewingOrgId } = await load();
    setViewingOrg({ id: "org-1", name: "Acme" });
    setViewingOrg(null);
    expect(viewingOrgId()).toBeNull();
    expect(sessionStorage.getItem("prabhix_viewing_org")).toBeNull();
  });

  it("notifies subscribers on change so the banner appears without a reload", async () => {
    const { setViewingOrg, subscribeToViewingOrg } = await load();
    const seen: (string | null)[] = [];
    subscribeToViewingOrg((org) => seen.push(org?.id ?? null));

    setViewingOrg({ id: "org-1", name: "Acme" });
    setViewingOrg({ id: "org-2", name: "Globex" });
    setViewingOrg(null);

    expect(seen).toEqual(["org-1", "org-2", null]);
  });

  it("does not notify when the same org is set again, avoiding a needless cache clear", async () => {
    const { setViewingOrg, subscribeToViewingOrg } = await load();
    const listener = vi.fn();
    subscribeToViewingOrg(listener);

    setViewingOrg({ id: "org-1", name: "Acme" });
    setViewingOrg({ id: "org-1", name: "Acme" });

    expect(listener).toHaveBeenCalledTimes(1);
  });

  it("stops notifying after unsubscribe", async () => {
    const { setViewingOrg, subscribeToViewingOrg } = await load();
    const listener = vi.fn();
    const unsubscribe = subscribeToViewingOrg(listener);
    unsubscribe();

    setViewingOrg({ id: "org-1", name: "Acme" });

    expect(listener).not.toHaveBeenCalled();
  });

  it("ignores a corrupt stored value rather than throwing on import", async () => {
    sessionStorage.setItem("prabhix_viewing_org", "{not json");
    const { viewingOrg } = await load();
    expect(viewingOrg()).toBeNull();
  });

  it("ignores a stored value missing a name, which would render an empty banner", async () => {
    sessionStorage.setItem("prabhix_viewing_org", JSON.stringify({ id: "org-1" }));
    const { viewingOrg } = await load();
    expect(viewingOrg()).toBeNull();
  });
});
