import { useEffect } from "react";
import { LogOut, Mail, Moon, Search, Sun } from "lucide-react";
import { NavLink, useLocation } from "react-router";
import { LogoMark } from "@/components/brand/LogoMark";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { cn } from "@/lib/utils";
import { useTheme } from "@/lib/theme";
import { useAuth } from "@/lib/auth";
import { IS_ADMIN_APP } from "@/lib/app-mode";
import { hasAnyPermission } from "@/lib/permissions";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { navGroups, type NavItem } from "./nav-config";
import { useStaffRoles } from "@/features/ops/api";

/**
 * Prabhix Mailroom, for a person's own mail as opposed to the shared inbox this console owns.
 *
 * Unset hides the link. Guessing the production host would put a dead link in every local build, and
 * unlike the admin handoff there is no action here that silently needs somewhere to go.
 */
const MAILROOM_URL: string = import.meta.env.VITE_MAILROOM_URL ?? "";

interface SidebarProps {
  onOpenCommand: () => void;
  onLogout: () => void;
  onNavigate?: () => void;
  className?: string;
}

export function Sidebar({ onOpenCommand, onLogout, onNavigate, className }: SidebarProps) {
  const { theme, toggleTheme } = useTheme();
  const { me, permissions } = useAuth();
  const staff = useStaffRoles();
  const groups = navGroups;

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    cn(
      "flex min-h-11 items-center gap-3 rounded-lg px-2.5 py-2 text-sm font-medium transition-colors hover:bg-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
      isActive ? "bg-surface text-primary shadow-sm" : "text-text-muted",
    );

  const navLink = (item: NavItem) => (
    <NavLink
      key={item.to}
      to={item.to}
      end={item.end ?? item.to === "/"}
      className={linkClass}
      onClick={onNavigate}
    >
      <item.icon className="h-4 w-4 shrink-0" aria-hidden="true" />
      <span className="lg:inline">{item.label}</span>
    </NavLink>
  );

  const renderItem = (item: NavItem) => {
    if (item.platformAdminOnly && !me?.platformAdmin) return null;
    if (item.staffRoles && staff.isSuccess) {
      const roles = staff.data.roles ?? [];
      const allowed = roles.includes("OWNER") || item.staffRoles.some((role) => roles.includes(role));
      if (!allowed) return null;
    }
    if (item.anyPermission && !hasAnyPermission(permissions, item.anyPermission)) return null;
    const link = navLink(item);
    if (!item.permission) return link;
    return (
      <PermissionGate key={item.to} permission={item.permission}>
        {link}
      </PermissionGate>
    );
  };

  return (
    <aside
      className={cn(
        "flex h-full w-full flex-col border-r border-border bg-surface lg:w-56",
        className,
      )}
    >
      <div className="flex h-14 items-center gap-2 border-b border-border px-3 lg:px-4">
        <LogoMark className="h-8 w-8 shrink-0" />
        <span className="text-sm font-semibold">Prabhix</span>
        {IS_ADMIN_APP && (
          <span className="rounded bg-primary/10 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-primary">
            Admin
          </span>
        )}
      </div>

      <nav className="flex-1 overflow-y-auto p-2" aria-label="Main navigation">
        {groups.map((group) => {
          const items = group.items.map(renderItem).filter(Boolean);
          // A group whose every link is hidden by permissions would otherwise leave a stray heading.
          if (items.length === 0) return null;
          return (
            <div key={group.heading} className="mb-3 space-y-1 last:mb-0">
              <p className={cn(
                "px-2 pb-1 text-[10px] font-semibold uppercase tracking-wider text-text-muted",
                group.nested && "mt-1 pl-4",
              )}>
                {group.heading}
              </p>
              <div className={group.nested ? "pl-2" : undefined}>
                {items}
              </div>
            </div>
          );
        })}
      </nav>

      <div className="space-y-1 border-t border-border p-2 pb-[env(safe-area-inset-bottom)]">
        <Button variant="ghost" size="sm" className="w-full justify-start gap-3" onClick={onOpenCommand}>
          <Search className="h-4 w-4" aria-hidden="true" />
          <span>Search</span>
          <kbd className="ml-auto hidden rounded bg-surface px-1.5 text-[10px] lg:inline">⌘K</kbd>
        </Button>
        <Button
          variant="ghost"
          size="sm"
          className="w-full justify-start gap-3"
          onClick={toggleTheme}
          aria-label="Toggle theme"
        >
          {theme === "dark" ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
          <span>{theme === "dark" ? "Light mode" : "Dark mode"}</span>
        </Button>
        {MAILROOM_URL && (
          <Button variant="ghost" size="sm" className="w-full justify-start gap-3" asChild>
            <a href={MAILROOM_URL} target="_blank" rel="noopener noreferrer">
              <Mail className="h-4 w-4" aria-hidden="true" />
              <span>My mail</span>
            </a>
          </Button>
        )}
        <Separator className="my-1" />
        <Button
          variant="ghost"
          size="sm"
          className="w-full justify-start gap-3 text-destructive"
          onClick={onLogout}
        >
          <LogOut className="h-4 w-4" aria-hidden="true" />
          <span>Sign out</span>
        </Button>
      </div>
    </aside>
  );
}

export function useCloseNavOnRouteChange(onClose: () => void) {
  const location = useLocation();
  useEffect(() => {
    onClose();
  }, [location.pathname, onClose]);
}
