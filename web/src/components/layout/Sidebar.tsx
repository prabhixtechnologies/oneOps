import { useEffect } from "react";
import {
  Activity,
  CreditCard,
  FileText,
  Flag,
  FolderOpen,
  Globe,
  Inbox,
  LayoutDashboard,
  LogOut,
  Mail,
  MessageSquare,
  Moon,
  ScrollText,
  Search,
  Settings,
  Sun,
  Tag,
  Users,
  Briefcase,
  ShoppingBag,
  Receipt,
  Percent,
  Store,
  Sparkles,
  BarChart3,
} from "lucide-react";
import { NavLink, useLocation } from "react-router";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { cn } from "@/lib/utils";
import { useTheme } from "@/lib/theme";
import { useAuth } from "@/lib/auth";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { PERMISSIONS } from "@/lib/permissions";

const navItems = [
  { to: "/", icon: LayoutDashboard, label: "Dashboard" },
  { to: "/chat", icon: MessageSquare, label: "Live Chat", permission: PERMISSIONS.CHAT_READ },
  { to: "/visitors", icon: Activity, label: "Visitors", permission: PERMISSIONS.VISITOR_READ },
  { to: "/inbox", icon: Inbox, label: "Shared Inbox" },
  { to: "/mailboxes", icon: Mail, label: "Mailboxes", permission: PERMISSIONS.MAIL_MAILBOX_READ },
  { to: "/domains", icon: Globe, label: "Mail Domains", permission: PERMISSIONS.MAIL_DOMAIN_READ },
  { to: "/templates", icon: FileText, label: "Templates", permission: PERMISSIONS.MAIL_TEMPLATE_READ },
  { to: "/tags", icon: Tag, label: "Mail tags", permission: PERMISSIONS.MAIL_READ },
  { to: "/canned-replies", icon: MessageSquare, label: "Canned replies", permission: PERMISSIONS.MAIL_READ },
  { to: "/files", icon: FolderOpen, label: "Files", permission: PERMISSIONS.FILE_READ },
  { to: "/commerce", icon: Store, label: "Shop dashboard", permission: PERMISSIONS.COMMERCE_ORDER_READ },
  { to: "/commerce/products", icon: ShoppingBag, label: "Products", permission: PERMISSIONS.COMMERCE_CATALOG_READ },
  { to: "/commerce/orders", icon: Receipt, label: "Orders", permission: PERMISSIONS.COMMERCE_ORDER_READ },
  { to: "/commerce/customers", icon: Users, label: "Customers", permission: PERMISSIONS.COMMERCE_CUSTOMER_READ },
  { to: "/commerce/discounts", icon: Percent, label: "Discounts", permission: PERMISSIONS.COMMERCE_DISCOUNT_MANAGE },
  { to: "/commerce/settings", icon: Store, label: "Shop settings", permission: PERMISSIONS.COMMERCE_SETTINGS_MANAGE },
  { to: "/members", icon: Users, label: "Members & Roles", permission: PERMISSIONS.ORG_MEMBER_READ },
  { to: "/billing", icon: CreditCard, label: "Billing", permission: PERMISSIONS.BILLING_READ },
  { to: "/ai/settings", icon: Sparkles, label: "AI settings", permission: PERMISSIONS.AI_CONFIGURE },
  { to: "/ai/usage", icon: BarChart3, label: "AI usage", permission: PERMISSIONS.AI_USAGE_READ },
  { to: "/audit", icon: FileText, label: "Audit Log", permission: PERMISSIONS.AUDIT_READ },
  { to: "/logs", icon: ScrollText, label: "Event Logs", permission: PERMISSIONS.LOG_READ },
  { to: "/flags", icon: Flag, label: "Feature flags" },
  { to: "/site", icon: Briefcase, label: "Site admin" },
  { to: "/settings", icon: Settings, label: "Settings" },
];

interface SidebarProps {
  onOpenCommand: () => void;
  onLogout: () => void;
  onNavigate?: () => void;
  className?: string;
}

export function Sidebar({ onOpenCommand, onLogout, onNavigate, className }: SidebarProps) {
  const { theme, toggleTheme } = useTheme();
  const { me } = useAuth();

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    cn(
      "flex items-center gap-3 rounded-md px-2 py-2 text-sm font-medium transition-colors hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
      isActive ? "bg-surface text-primary" : "text-text-muted",
    );

  const navLink = (item: (typeof navItems)[number]) => (
    <NavLink
      key={item.to}
      to={item.to}
      end={item.to === "/"}
      className={linkClass}
      onClick={onNavigate}
    >
      <item.icon className="h-4 w-4 shrink-0" aria-hidden="true" />
      <span className="lg:inline">{item.label}</span>
    </NavLink>
  );

  return (
    <aside
        className={cn(
          "flex h-full w-full flex-col border-r border-border bg-surface-muted/50 lg:w-52",
          className,
        )}
      >
        <div className="flex h-14 items-center gap-2 border-b border-border px-3 lg:px-4">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-sm font-bold text-white">
            P
          </div>
          <span className="text-sm font-semibold">Prabhix</span>
        </div>

        <nav className="flex-1 space-y-1 overflow-y-auto p-2" aria-label="Main navigation">
          {navItems.map((item) => {
            if (item.to === "/site" && !me?.platformAdmin) return null;
            const link = navLink(item);
            if (item.permission) {
              return (
                <PermissionGate key={item.to} permission={item.permission}>
                  {link}
                </PermissionGate>
              );
            }
            return link;
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
