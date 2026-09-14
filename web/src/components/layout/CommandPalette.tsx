import {
  Activity,
  Briefcase,
  Building2,
  Cloud,
  CreditCard,
  FileText,
  Flag,
  FolderOpen,
  Inbox,
  KeyRound,
  LayoutDashboard,
  Mail,
  MessageSquare,
  Percent,
  Receipt,
  ScrollText,
  Settings,
  Shield,
  ShoppingBag,
  Sparkles,
  Store,
  Users,
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router";
import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
} from "@/components/ui/command";
import { IS_ADMIN_APP } from "@/lib/app-mode";

const tenantPages = [
  { label: "Overview", to: "/", icon: LayoutDashboard },
  { label: "Inbox", to: "/inbox", icon: Inbox },
  { label: "Live Chat", to: "/chat", icon: MessageSquare },
  { label: "Visitors", to: "/visitors", icon: Activity },
  { label: "Shop dashboard", to: "/commerce", icon: Store },
  { label: "Products", to: "/commerce/products", icon: ShoppingBag },
  { label: "Orders", to: "/commerce/orders", icon: Receipt },
  { label: "Discounts", to: "/commerce/discounts", icon: Percent },
  { label: "Members", to: "/members", icon: Users },
  { label: "Billing", to: "/billing", icon: CreditCard },
  { label: "Org settings", to: "/settings", icon: Settings },
  { label: "Mail settings", to: "/settings/mail", icon: Mail },
  { label: "AI settings", to: "/ai/settings", icon: Sparkles },
  { label: "AI usage", to: "/ai/usage", icon: Sparkles },
  { label: "API keys", to: "/settings/api-keys", icon: KeyRound },
  { label: "Flags", to: "/flags", icon: Flag },
  { label: "Event logs", to: "/logs", icon: ScrollText },
  { label: "Audit Log", to: "/audit", icon: FileText },
  { label: "Files", to: "/files", icon: FolderOpen },
];

const adminPages = [
  { label: "Overview", to: "/", icon: Briefcase },
  { label: "Tenants and shops", to: "/tenants", icon: Building2 },
  { label: "Identity", to: "/identity", icon: Shield },
  { label: "Revenue", to: "/revenue", icon: CreditCard },
  { label: "Infra", to: "/infra", icon: Cloud },
  { label: "Mail health", to: "/mail", icon: Mail },
  { label: "Staff", to: "/staff", icon: Users },
  { label: "MobiStack ops", to: "/mobistack", icon: Store },
  { label: "Commons review", to: "/commons", icon: Flag },
  { label: "Event logs", to: "/logs", icon: ScrollText },
];

const pages = IS_ADMIN_APP ? adminPages : tenantPages;

interface CommandPaletteProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function CommandPalette({ open, onOpenChange }: CommandPaletteProps) {
  const navigate = useNavigate();

  const run = useCallback(
    (to: string) => {
      onOpenChange(false);
      void navigate(to);
    },
    [navigate, onOpenChange],
  );

  return (
    <CommandDialog open={open} onOpenChange={onOpenChange}>
      <CommandInput placeholder="Search pages and actions…" />
      <CommandList>
        <CommandEmpty>No results found.</CommandEmpty>
        <CommandGroup heading="Navigation">
          {pages.map(({ label, to, icon: Icon }) => (
            <CommandItem key={to} onSelect={() => run(to)}>
              <Icon className="h-4 w-4" />
              {label}
            </CommandItem>
          ))}
        </CommandGroup>
        {!IS_ADMIN_APP && (
          <>
            <CommandSeparator />
            <CommandGroup heading="Actions">
              <CommandItem onSelect={() => run("/members?invite=true")}>
                <Users className="h-4 w-4" />
                Invite team member
              </CommandItem>
            </CommandGroup>
          </>
        )}
      </CommandList>
    </CommandDialog>
  );
}

export function useCommandPalette() {
  const [open, setOpen] = useState(false);

  useEffect(() => {
    const down = (e: KeyboardEvent) => {
      if (e.key === "k" && (e.metaKey || e.ctrlKey)) {
        e.preventDefault();
        setOpen((o) => !o);
      }
    };
    document.addEventListener("keydown", down);
    return () => document.removeEventListener("keydown", down);
  }, []);

  return { open, setOpen };
}
