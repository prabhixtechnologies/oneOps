import { useMemo, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import { AlertTriangle, Inbox, Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { EmptyState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { getApiErrorMessage } from "@/lib/api-client";
import { useAuth } from "@/lib/auth";
import { useAssignableMembers } from "@/features/helpdesk/api";
import {
  useAdminMailboxes,
  useCreateMailbox,
  useDeleteMailbox,
  useMailDomains,
  type MailboxAdminSummary,
} from "@/features/helpdesk/mailbox-admin";
import { adminErrorHint } from "./MailSettingsLayout";
import { ConfirmDialog } from "./ConfirmDialog";

function localPartFromEmail(email: string) {
  const at = email.indexOf("@");
  const raw = (at >= 0 ? email.slice(0, at) : email).toLowerCase();
  return raw.replace(/[^a-z0-9._-]/g, "") || "member";
}

export default function MailboxesPage() {
  const { me } = useAuth();
  const mailboxes = useAdminMailboxes();
  const create = useCreateMailbox();
  const remove = useDeleteMailbox();
  const navigate = useNavigate();
  const members = useAssignableMembers(me?.organizationId ?? undefined);
  const domains = useMailDomains();
  const verifiedDomains = useMemo(
    () => (domains.data ?? []).filter((d) => d.status === "VERIFIED"),
    [domains.data],
  );

  const [showCreate, setShowCreate] = useState(false);
  const [createKind, setCreateKind] = useState<"SHARED" | "PERSONAL">("SHARED");
  const [address, setAddress] = useState("");
  const [name, setName] = useState("");
  const [ownerUserId, setOwnerUserId] = useState("");
  const [domain, setDomain] = useState("");
  const [localPart, setLocalPart] = useState("");
  const [error, setError] = useState<string>();
  const [confirmDelete, setConfirmDelete] = useState<MailboxAdminSummary>();

  const selectedMember = (members.data?.items ?? []).find((m) => m.userId === ownerUserId);

  const pickOwner = (userId: string) => {
    setOwnerUserId(userId);
    const member = (members.data?.items ?? []).find((m) => m.userId === userId);
    if (member) {
      setName(member.displayName?.trim() || member.email || "");
      setLocalPart(localPartFromEmail(member.email ?? ""));
    }
  };

  const submitCreate = async (e: FormEvent) => {
    e.preventDefault();
    setError(undefined);
    try {
      const created =
        createKind === "PERSONAL"
          ? await create.mutateAsync({
              address: `${localPart.trim()}@${domain}`.toLowerCase(),
              name: name.trim(),
              kind: "PERSONAL",
              ownerUserId,
            })
          : await create.mutateAsync({
              address: address.trim(),
              name: name.trim(),
              kind: "SHARED",
            });
      setShowCreate(false);
      setAddress("");
      setName("");
      setOwnerUserId("");
      setDomain("");
      setLocalPart("");
      navigate(`/settings/mail/${created.id}`);
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  const doDelete = async () => {
    if (!confirmDelete) return;
    setError(undefined);
    try {
      await remove.mutateAsync(confirmDelete.id);
      setConfirmDelete(undefined);
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  return (
    <div className="mx-auto max-w-3xl p-6">
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="font-display text-xl font-semibold tracking-tight">Mailboxes</h1>
          <p className="mt-1 text-sm text-text-muted">
            Shared inboxes for the helpdesk, and personal addresses for members.
          </p>
        </div>
        <Button size="sm" onClick={() => setShowCreate((v) => !v)}>
          <Plus className="size-4" />
          Add mailbox
        </Button>
      </div>

      {error ? (
        <p className="mb-4 rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </p>
      ) : null}

      {showCreate ? (
        <form
          onSubmit={(e) => void submitCreate(e)}
          className="mb-6 space-y-3 rounded-lg border border-border p-4"
        >
          <h2 className="text-sm font-medium">
            {createKind === "PERSONAL" ? "Mailbox for a member" : "New shared mailbox"}
          </h2>
          <div className="flex gap-1 rounded-md bg-surface-muted p-1">
            <button
              type="button"
              onClick={() => setCreateKind("SHARED")}
              className={`min-h-9 flex-1 rounded px-2 text-xs font-medium ${
                createKind === "SHARED" ? "bg-surface shadow-sm" : "text-text-muted"
              }`}
            >
              Shared
            </button>
            <button
              type="button"
              onClick={() => setCreateKind("PERSONAL")}
              className={`min-h-9 flex-1 rounded px-2 text-xs font-medium ${
                createKind === "PERSONAL" ? "bg-surface shadow-sm" : "text-text-muted"
              }`}
            >
              For a member
            </button>
          </div>

          {createKind === "PERSONAL" ? (
            <>
              <div>
                <label className="mb-1 block text-xs text-text-muted" htmlFor="mb-owner">
                  Member
                </label>
                <select
                  id="mb-owner"
                  className="h-10 w-full rounded-md border border-border bg-surface px-3 text-sm"
                  value={ownerUserId}
                  onChange={(e) => pickOwner(e.target.value)}
                  required
                >
                  <option value="">Select a member…</option>
                  {(members.data?.items ?? []).map((m) => (
                    <option key={m.userId} value={m.userId}>
                      {m.displayName ?? m.email ?? m.userId}
                      {m.email ? ` · ${m.email}` : ""}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="mb-1 block text-xs text-text-muted" htmlFor="mb-domain">
                  Verified domain
                </label>
                <select
                  id="mb-domain"
                  className="h-10 w-full rounded-md border border-border bg-surface px-3 text-sm"
                  value={domain}
                  onChange={(e) => setDomain(e.target.value)}
                  required
                >
                  <option value="">Select a domain…</option>
                  {verifiedDomains.map((d) => (
                    <option key={d.id} value={d.domain}>
                      {d.domain}
                    </option>
                  ))}
                </select>
                {verifiedDomains.length === 0 ? (
                  <p className="mt-1 text-xs text-text-muted">
                    Verify a sending domain under Settings → Mail → Domains first.
                  </p>
                ) : null}
              </div>
              <div>
                <label className="mb-1 block text-xs text-text-muted" htmlFor="mb-local">
                  Local part
                </label>
                <div className="flex items-center gap-2">
                  <Input
                    id="mb-local"
                    value={localPart}
                    onChange={(e) => setLocalPart(e.target.value)}
                    placeholder="alice"
                    required
                  />
                  <span className="shrink-0 text-sm text-text-muted">@{domain || "domain"}</span>
                </div>
              </div>
              <div>
                <label className="mb-1 block text-xs text-text-muted" htmlFor="mb-name">
                  Display name
                </label>
                <Input
                  id="mb-name"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder={selectedMember?.displayName ?? "Alice"}
                  required
                />
              </div>
            </>
          ) : (
            <>
              <div>
                <label className="mb-1 block text-xs text-text-muted" htmlFor="mb-address">
                  Address
                </label>
                <Input
                  id="mb-address"
                  value={address}
                  onChange={(e) => setAddress(e.target.value)}
                  placeholder="support@yourcompany.com"
                  required
                />
              </div>
              <div>
                <label className="mb-1 block text-xs text-text-muted" htmlFor="mb-name">
                  Display name
                </label>
                <Input
                  id="mb-name"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="Support"
                  required
                />
              </div>
            </>
          )}
          <div className="flex gap-2">
            <Button
              type="submit"
              disabled={
                create.isPending || (createKind === "PERSONAL" && verifiedDomains.length === 0)
              }
            >
              {create.isPending ? "Creating…" : "Create"}
            </Button>
            <Button type="button" variant="outline" onClick={() => setShowCreate(false)}>
              Cancel
            </Button>
          </div>
        </form>
      ) : null}

      {mailboxes.isPending ? (
        <div className="space-y-2">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-16 w-full" />
          ))}
        </div>
      ) : mailboxes.isError ? (
        <EmptyState
          icon={<AlertTriangle className="size-8" />}
          title="Mailboxes could not be loaded"
          description={adminErrorHint(mailboxes.error) ?? getApiErrorMessage(mailboxes.error)}
        />
      ) : (mailboxes.data ?? []).length === 0 ? (
        <EmptyState
          icon={<Inbox className="size-8" />}
          title="No mailboxes yet"
          description="Add a shared mailbox for the inbox, or create an address for a member."
        />
      ) : (
        <ul className="divide-y divide-border rounded-lg border border-border">
          {(mailboxes.data ?? []).map((mb) => (
            <li key={mb.id} className="flex items-center gap-3 px-4 py-3">
              <Link
                to={`/settings/mail/${mb.id}`}
                className="min-w-0 flex-1 hover:text-primary"
              >
                <div className="truncate text-sm font-medium">{mb.name}</div>
                <div className="truncate text-xs text-text-muted">{mb.address}</div>
              </Link>
              <Badge variant="secondary">{mb.kind.toLowerCase()}</Badge>
              <span className="shrink-0 text-xs text-text-muted">
                {mb.openThreadCount} open
              </span>
              <Button
                variant="ghost"
                size="icon"
                aria-label={`Delete ${mb.name}`}
                onClick={() => setConfirmDelete(mb)}
              >
                <Trash2 className="size-4 text-destructive" />
              </Button>
            </li>
          ))}
        </ul>
      )}

      {confirmDelete ? (
        <ConfirmDialog
          title={`Delete ${confirmDelete.name}?`}
          hint="The mailbox will be archived. Existing threads stay in the database but no new mail is accepted."
          busy={remove.isPending}
          onCancel={() => setConfirmDelete(undefined)}
          onConfirm={() => void doDelete()}
        />
      ) : null}
    </div>
  );
}
