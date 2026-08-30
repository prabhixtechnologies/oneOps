import {
  Activity,
  CreditCard,
  FileText,
  LayoutDashboard,
  MessageSquare,
  Settings,
  Sparkles,
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

const pages = [
  { label: "Dashboard", to: "/", icon: LayoutDashboard },
  { label: "Live Chat", to: "/chat", icon: MessageSquare },
  { label: "Visitors", to: "/visitors", icon: Activity },
  { label: "Members & Roles", to: "/members", icon: Users },
  { label: "Billing", to: "/billing", icon: CreditCard },
  { label: "AI settings", to: "/ai/settings", icon: Sparkles },
  { label: "AI usage", to: "/ai/usage", icon: Sparkles },
  { label: "Audit Log", to: "/audit", icon: FileText },
  { label: "Settings", to: "/settings", icon: Settings },
];

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
        <CommandSeparator />
        <CommandGroup heading="Actions">
          <CommandItem onSelect={() => run("/members?invite=true")}>
            <Users className="h-4 w-4" />
            Invite team member
          </CommandItem>
        </CommandGroup>
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
