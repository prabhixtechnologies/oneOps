import { LogOut, Mail, Moon, Sun, UserPlus } from "lucide-react";
import { useMemo, type ReactNode } from "react";
import { useNavigate } from "react-router";
import { CommandPaletteProvider, useCommandPalette, type PaletteCommand } from "@prabhix/ui";
import { IS_ADMIN_APP } from "@/lib/app-mode";
import { useAuth } from "@/lib/auth";
import { useTheme } from "@/lib/theme";
import { useVisibleNav } from "./use-visible-nav";

export { useCommands } from "@prabhix/ui";
export { useCommandPalette };

/*
  Words people type that are not the word on the link.

  Nobody searches for "Commerce" when they want to change a price, and nobody types "Visitors"
  looking for who is on the site right now. Without these the palette only works for someone who
  already knows the menu, which is the one person who did not need it.

  Split into two maps and chosen at module scope for the same reason nav-config.ts splits its
  groups: the branch not taken is dropped from the bundle along with every route string in it.
  Written as one map, this leaked `/commerce/orders` and `/commerce/products` into the admin
  console's bundle, and CI caught it - the two builds are checked for each other's strings
  precisely so that staff surfaces and customer surfaces cannot learn about each other.
*/
const tenantSynonyms: Record<string, string[]> = {
  "/": ["home", "dashboard", "start"],
  "/inbox": ["mail", "email", "tickets", "support", "threads", "replies"],
  "/chat": ["livechat", "messages", "widget"],
  "/visitors": ["traffic", "sessions", "who is online"],
  "/commerce": ["shop", "store", "sales"],
  "/commerce/products": ["catalog", "catalogue", "stock", "inventory", "sku", "price", "pricing"],
  "/commerce/orders": ["purchases", "refund", "fulfilment", "fulfillment", "shipping"],
  "/commerce/customers": ["buyers", "contacts", "people", "accounts"],
  "/commerce/discounts": ["coupons", "promo", "vouchers", "sale", "offer"],
  "/commerce/settings": ["shipping", "tax", "payments", "checkout", "currency"],
  "/members": ["team", "users", "invite", "roles", "permissions", "access"],
  "/billing": ["invoice", "plan", "payment", "card", "upgrade"],
  "/settings": ["organisation", "organization", "org", "profile", "name", "logo", "branding"],
  "/settings/mail": ["dns", "domain", "spf", "dkim", "mailbox", "smtp", "imap", "sending"],
  "/settings/api-keys": ["token", "secret", "integration", "webhook", "developer"],
  "/ai/settings": ["assistant", "model", "prompt", "automation"],
  "/ai/usage": ["spend", "tokens", "cost", "quota"],
  "/flags": ["feature flags", "toggles", "rollout", "experiments"],
  "/logs": ["events", "activity", "history", "debug"],
  "/audit": ["compliance", "who changed", "trail", "security"],
  "/files": ["uploads", "attachments", "storage", "documents"],
};

const platformSynonyms: Record<string, string[]> = {
  "/": ["home", "dashboard", "start"],
  "/tenants": ["customers", "orgs", "organisations", "accounts", "shops"],
  "/identity": ["auth", "sso", "oidc", "login", "sessions", "mfa"],
  "/revenue": ["mrr", "arr", "money", "income", "subscriptions"],
  "/infra": ["servers", "health", "uptime", "deploys", "aws"],
  "/mail": ["deliverability", "bounces", "queue", "smtp health"],
  "/staff": ["employees", "platform team", "admins"],
  "/mobistack": ["repairs", "fixflow", "workshop"],
  "/commons": ["moderation", "reports", "review queue"],
  "/logs": ["events", "activity", "history", "debug"],
};

const SYNONYMS = IS_ADMIN_APP ? platformSynonyms : tenantSynonyms;

const MAILROOM_URL: string = import.meta.env.VITE_MAILROOM_URL ?? "";

/**
 * Wraps the app so that any page can add commands while it is open.
 *
 * <p>Navigation is generated from the same filtered nav the sidebar renders, so the palette can no
 * longer offer a page that is not there. What it adds on top is the things that were previously
 * only reachable by finding the right button: switching theme, signing out, inviting someone.
 */
export function AppCommandPalette({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const { logout } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const groups = useVisibleNav();

  const commands = useMemo<PaletteCommand[]>(() => {
    const go = (to: string) => () => void navigate(to);

    const pages: PaletteCommand[] = groups.flatMap((group, groupIndex) =>
      group.items.map((item, itemIndex) => ({
        id: `nav:${item.to}:${item.label}`,
        label: item.label,
        group: group.heading,
        keywords: SYNONYMS[item.to],
        icon: <item.icon className="h-4 w-4" aria-hidden="true" />,
        // Keeps the palette in the sidebar's reading order rather than alphabetising it, so
        // muscle memory built on the sidebar still applies.
        order: groupIndex * 100 + itemIndex,
        perform: go(item.to),
      })),
    );

    const actions: PaletteCommand[] = [
      {
        id: "theme",
        label: theme === "dark" ? "Switch to light mode" : "Switch to dark mode",
        group: "Preferences",
        keywords: ["theme", "dark", "light", "appearance", "contrast"],
        icon: theme === "dark" ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />,
        perform: toggleTheme,
      },
      {
        id: "sign-out",
        label: "Sign out",
        group: "Preferences",
        keywords: ["log out", "logout", "leave", "exit"],
        icon: <LogOut className="h-4 w-4" />,
        perform: () => void logout(),
      },
    ];

    if (MAILROOM_URL) {
      actions.unshift({
        id: "mailroom",
        label: "Open my mail",
        description: "Prabhix Mailroom, in a new tab",
        group: "Preferences",
        keywords: ["mailroom", "personal", "my inbox"],
        icon: <Mail className="h-4 w-4" />,
        perform: () => window.open(MAILROOM_URL, "_blank", "noopener,noreferrer"),
      });
    }

    // Only where the page exists to receive it. The admin console has no members page, and a
    // command that navigates nowhere is worse than one that is absent.
    if (!IS_ADMIN_APP && groups.some((g) => g.items.some((i) => i.to === "/members"))) {
      actions.unshift({
        id: "invite",
        label: "Invite a team member",
        group: "Create",
        keywords: ["add user", "new member", "staff", "colleague"],
        icon: <UserPlus className="h-4 w-4" />,
        perform: go("/members?invite=true"),
      });
    }

    return [...pages, ...actions];
  }, [groups, navigate, logout, theme, toggleTheme]);

  return (
    <CommandPaletteProvider staticCommands={commands} placeholder="Search pages and actions…">
      {children}
    </CommandPaletteProvider>
  );
}
