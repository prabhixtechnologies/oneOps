import { useCallback, useState } from "react";
import { Outlet } from "react-router";
import { CommandPalette, useCommandPalette } from "@/components/layout/CommandPalette";
import { Sidebar, useCloseNavOnRouteChange } from "@/components/layout/Sidebar";
import { Topbar } from "@/components/layout/Topbar";
import { ViewingOrgBanner } from "@/components/layout/ViewingOrgBanner";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { IS_ADMIN_APP } from "@/lib/app-mode";
import { useAuth } from "@/lib/auth";
import { setAuthTokenBridge } from "@/lib/auth-token-bridge";
import { useStaffHandoff } from "@/lib/use-viewing-org";
import { useEffect } from "react";

/**
 * Only OneOps can be handed a customer to open, and only it has pages to show them in.
 *
 * <p>Chosen here rather than at the call site because a hook cannot be called conditionally. The
 * condition is a build constant, so this folds to one of the two and the admin bundle keeps neither
 * the hook nor the query parameter it looks for.
 */
const useHandoff: typeof useStaffHandoff = IS_ADMIN_APP ? () => {} : useStaffHandoff;

export function AppShell() {
  const { logout, accessToken, me } = useAuth();
  const { open, setOpen } = useCommandPalette();
  const [navOpen, setNavOpen] = useState(false);

  useHandoff();

  const closeNav = useCallback(() => setNavOpen(false), []);
  useCloseNavOnRouteChange(closeNav);

  useEffect(() => {
    setAuthTokenBridge(
      () => accessToken,
      () => me?.organizationId ?? null,
    );
  }, [accessToken, me?.organizationId]);

  return (
    <div className="flex h-[100dvh] overflow-hidden bg-surface">
      <div className="hidden lg:flex">
        <Sidebar onOpenCommand={() => setOpen(true)} onLogout={() => void logout()} />
      </div>

      <Dialog open={navOpen} onOpenChange={setNavOpen}>
        <DialogContent
          className="fixed inset-y-0 left-0 z-50 h-full w-[min(100%,280px)] max-w-none translate-x-0 translate-y-0 rounded-none border-r p-0 data-[state=closed]:slide-out-to-left data-[state=open]:slide-in-from-left motion-reduce:transition-none sm:max-w-none"
          aria-describedby={undefined}
        >
          <Sidebar
            onOpenCommand={() => {
              setNavOpen(false);
              setOpen(true);
            }}
            onLogout={() => void logout()}
            onNavigate={closeNav}
          />
        </DialogContent>
      </Dialog>

      <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
        <Topbar onOpenNav={() => setNavOpen(true)} />
        {/* Outside <main> so it stays put while the page scrolls. Renders nothing unless platform
            staff are inside a customer's organization, which is the only case there is: the admin
            console has no tenant pages to put a banner above. */}
        <ViewingOrgBanner />
        <main className="flex-1 overflow-auto pb-[env(safe-area-inset-bottom)]" id="main-content">
          <Outlet />
        </main>
      </div>
      <CommandPalette open={open} onOpenChange={setOpen} />
    </div>
  );
}
