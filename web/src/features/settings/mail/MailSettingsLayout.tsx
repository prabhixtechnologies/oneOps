import { Link, Navigate, Outlet, useLocation } from "react-router";
import { Settings } from "lucide-react";
import { EmptyState } from "@/components/shared/states";
import { PageHeader } from "@/components/shared/PageHeader";
import { useAuth } from "@/lib/auth";
import { ApiClientError } from "@/lib/api-client";
import {
  PERMISSION_MAILBOX_MANAGE,
  PERMISSION_THREAD_UPDATE,
} from "@/features/helpdesk/mailbox-admin";
import { PERMISSIONS } from "@/lib/permissions";
import { cn } from "@/lib/utils";

/**
 * Settings → Mail: mailboxes, routing, tags, canned replies, and sending domains.
 *
 * Mailbox and routing writes need MAIL_MAILBOX_MANAGE; tag and canned-reply writes need
 * MAIL_THREAD_UPDATE, because agents create those while triaging. Domain DNS is a third
 * permission. Each section is listed only when its own permission is held.
 */
export default function MailSettingsLayout() {
  const { permissions } = useAuth();
  const location = useLocation();
  const canManageMailboxes = permissions.includes(PERMISSION_MAILBOX_MANAGE);
  const canManageVocabulary = permissions.includes(PERMISSION_THREAD_UPDATE);
  const canReadDomains = permissions.includes(PERMISSIONS.MAIL_DOMAIN_READ);

  if (!canManageMailboxes && !canManageVocabulary && !canReadDomains) {
    return (
      <div className="p-4 md:p-6">
        <PageHeader title="Mail" description="Shared inboxes, routing, and sending domains." />
        <EmptyState
          icon={<Settings className="size-8" />}
          title="You do not have permission to change mail settings"
          description="Ask an administrator for mailbox manage, thread update, or domain read."
        />
      </div>
    );
  }

  const nav = [
    { to: "/settings/mail", label: "Mailboxes", exact: true, allowed: canManageMailboxes },
    { to: "/settings/mail/tags", label: "Tags", exact: false, allowed: canManageVocabulary },
    {
      to: "/settings/mail/canned-replies",
      label: "Canned replies",
      exact: false,
      allowed: canManageVocabulary,
    },
    { to: "/settings/mail/domains", label: "Domains", exact: false, allowed: canReadDomains },
  ].filter((item) => item.allowed);

  if (location.pathname === "/settings/mail" && !canManageMailboxes) {
    const fallback = nav[0]?.to ?? "/settings";
    return <Navigate to={fallback} replace />;
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col md:flex-row">
      <aside className="hidden w-52 shrink-0 border-r border-border bg-surface p-3 md:block">
        <p className="px-3 pb-2 text-xs font-semibold uppercase tracking-wider text-text-muted">
          Mail
        </p>
        <nav className="space-y-0.5">
          {nav.map((item) => {
            const active = item.exact
              ? location.pathname === item.to
              : location.pathname.startsWith(item.to);
            return (
              <Link
                key={item.to}
                to={item.to}
                className={cn(
                  "flex min-h-11 items-center rounded-md px-3 py-2 text-sm transition-colors",
                  active
                    ? "bg-primary font-medium text-primary-foreground"
                    : "text-text-muted hover:bg-surface-muted hover:text-text",
                )}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>
      </aside>
      <nav className="flex gap-1 overflow-x-auto border-b border-border px-3 py-2 md:hidden">
        {nav.map((item) => {
          const active = item.exact
            ? location.pathname === item.to
            : location.pathname.startsWith(item.to);
          return (
            <Link
              key={`m-${item.to}`}
              to={item.to}
              className={cn(
                "inline-flex min-h-11 shrink-0 items-center rounded-md px-3 py-2 text-sm transition-colors",
                active
                  ? "bg-primary font-medium text-primary-foreground"
                  : "text-text-muted hover:bg-surface-muted hover:text-text",
              )}
            >
              {item.label}
            </Link>
          );
        })}
      </nav>
      <div className="min-h-0 min-w-0 flex-1 overflow-y-auto">
        <Outlet />
      </div>
    </div>
  );
}

/** Surfaces a 403 from the API as a permission message rather than a generic failure. */
export function adminErrorHint(err: unknown): string | undefined {
  if (err instanceof ApiClientError && err.status === 403) {
    return "You do not have permission to make that change.";
  }
  return undefined;
}
