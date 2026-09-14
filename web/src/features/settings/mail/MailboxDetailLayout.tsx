import { Link, NavLink, Outlet, useParams } from "react-router";
import { AlertTriangle, ChevronLeft } from "lucide-react";
import { EmptyState } from "@/components/shared/states";
import { Skeleton } from "@/components/ui/skeleton";
import { getApiErrorMessage } from "@/lib/api-client";
import { useMailboxDetail } from "@/features/helpdesk/mailbox-admin";
import { cn } from "@/lib/utils";
import { adminErrorHint } from "./MailSettingsLayout";

export default function MailboxDetailLayout() {
  const { mailboxId } = useParams<{ mailboxId: string }>();
  const detail = useMailboxDetail(mailboxId);

  if (detail.isPending) {
    return (
      <div className="p-6">
        <Skeleton className="mb-4 h-8 w-48" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (detail.isError || !detail.data) {
    return (
      <EmptyState
        icon={<AlertTriangle className="size-8" />}
        title="Mailbox could not be loaded"
        description={adminErrorHint(detail.error) ?? getApiErrorMessage(detail.error)}
      />
    );
  }

  const mb = detail.data;
  const base = `/settings/mail/${mailboxId}`;
  const tabs = [
    { to: base, label: "General", end: true },
    { to: `${base}/members`, label: "Members", end: false },
    { to: `${base}/routing`, label: "Routing rules", end: false },
  ];

  return (
    <div className="mx-auto max-w-3xl p-6">
      <Link
        to="/settings/mail"
        className="mb-4 inline-flex items-center gap-1 text-xs text-text-muted hover:text-text"
      >
        <ChevronLeft className="size-3" />
        All mailboxes
      </Link>

      <header className="mb-6">
        <h1 className="text-lg font-semibold">{mb.name}</h1>
        <p className="text-sm text-text-muted">{mb.email}</p>
      </header>

      <nav className="mb-6 flex gap-1 border-b border-border">
        {tabs.map((tab) => (
          <NavLink
            key={tab.to}
            to={tab.to}
            end={tab.end}
            className={({ isActive }) =>
              cn(
                "border-b-2 px-3 py-2 text-sm transition-colors",
                isActive
                  ? "border-primary font-medium text-primary"
                  : "border-transparent text-text-muted hover:text-text",
              )
            }
          >
            {tab.label}
          </NavLink>
        ))}
      </nav>

      <Outlet context={{ mailbox: mb }} />
    </div>
  );
}

export { MailboxGeneralSection } from "./MailboxGeneralSection";
export { MailboxMembersSection } from "./MailboxMembersSection";
export { MailboxRoutingSection } from "./MailboxRoutingSection";
