import { useState } from "react";
import { useOutletContext } from "react-router";
import { Plus, Trash2, Users } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/shared/states";
import { getApiErrorMessage } from "@/lib/api-client";
import { useAuth } from "@/lib/auth";
import { useAssignableMembers } from "@/features/helpdesk/api";
import {
  memberAccessLevels,
  useAddMailboxMember,
  useRemoveMailboxMember,
  useTeams,
  useUpdateMailboxMember,
  type MailboxDetail,
  type MailboxMember,
  type MemberAccessLevel,
} from "@/features/helpdesk/mailbox-admin";
import { ConfirmDialog } from "./ConfirmDialog";

export function MailboxMembersSection() {
  const { mailbox } = useOutletContext<{ mailbox: MailboxDetail }>();
  const { me } = useAuth();
  const orgMembers = useAssignableMembers(me?.organizationId ?? undefined);
  const teams = useTeams();
  const add = useAddMailboxMember();
  const update = useUpdateMailboxMember();
  const remove = useRemoveMailboxMember();

  const [showAdd, setShowAdd] = useState(false);
  const [grantKind, setGrantKind] = useState<"user" | "team">("user");
  const [userId, setUserId] = useState("");
  const [teamId, setTeamId] = useState("");
  const [accessLevel, setAccessLevel] = useState<MemberAccessLevel>("MEMBER");
  const [error, setError] = useState<string>();
  const [confirmRemove, setConfirmRemove] = useState<MailboxMember>();

  const existingUserIds = new Set(
    mailbox.members.filter((m) => m.userId).map((m) => m.userId as string),
  );
  const existingTeamIds = new Set(
    mailbox.members.filter((m) => m.teamId).map((m) => m.teamId as string),
  );

  const availableUsers = (orgMembers.data?.items ?? []).filter(
    (m) => !existingUserIds.has(m.userId),
  );
  const availableTeams = (teams.data?.items ?? []).filter((t) => !existingTeamIds.has(t.id));
  const canPickTeams = !teams.isError && availableTeams.length > 0;

  const submitAdd = async () => {
    setError(undefined);
    try {
      await add.mutateAsync({
        mailboxId: mailbox.id,
        userId: grantKind === "user" ? userId : undefined,
        teamId: grantKind === "team" ? teamId : undefined,
        accessLevel,
      });
      setShowAdd(false);
      setUserId("");
      setTeamId("");
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  const changeLevel = async (member: MailboxMember, level: MemberAccessLevel) => {
    setError(undefined);
    try {
      await update.mutateAsync({
        mailboxId: mailbox.id,
        memberId: member.id,
        accessLevel: level,
      });
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  const doRemove = async () => {
    if (!confirmRemove) return;
    setError(undefined);
    try {
      await remove.mutateAsync({ mailboxId: mailbox.id, memberId: confirmRemove.id });
      setConfirmRemove(undefined);
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <p className="text-sm text-text-muted">
          People and teams who can read and work this mailbox.
        </p>
        <Button size="sm" onClick={() => setShowAdd((v) => !v)}>
          <Plus className="size-4" />
          Add member
        </Button>
      </div>

      {error ? (
        <p className="mb-4 rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </p>
      ) : null}

      {showAdd ? (
        <div className="mb-4 space-y-3 rounded-lg border border-border p-4">
          {canPickTeams ? (
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setGrantKind("user")}
                className={`rounded-md px-3 py-1 text-xs ${
                  grantKind === "user"
                    ? "bg-primary/15 text-primary"
                    : "text-text-muted hover:bg-surface-muted"
                }`}
              >
                User
              </button>
              <button
                type="button"
                onClick={() => setGrantKind("team")}
                className={`rounded-md px-3 py-1 text-xs ${
                  grantKind === "team"
                    ? "bg-primary/15 text-primary"
                    : "text-text-muted hover:bg-surface-muted"
                }`}
              >
                Team
              </button>
            </div>
          ) : null}

          {grantKind === "user" ? (
            orgMembers.isError ? (
              <p className="text-xs text-text-muted">
                Cannot list organization members — you may lack directory read access.
              </p>
            ) : (
              <select
                aria-label="Choose a user"
                value={userId}
                onChange={(e) => setUserId(e.target.value)}
                className="w-full rounded-md border border-border bg-surface px-3 py-2 text-sm"
              >
                <option value="">Select a user…</option>
                {availableUsers.map((m) => (
                  <option key={m.userId} value={m.userId}>
                    {m.displayName ?? m.email ?? m.userId}
                  </option>
                ))}
              </select>
            )
          ) : (
            <select
              aria-label="Choose a team"
              value={teamId}
              onChange={(e) => setTeamId(e.target.value)}
              className="w-full rounded-md border border-border bg-surface px-3 py-2 text-sm"
            >
              <option value="">Select a team…</option>
              {availableTeams.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          )}

          <select
            aria-label="Access level"
            value={accessLevel}
            onChange={(e) => setAccessLevel(e.target.value as MemberAccessLevel)}
            className="w-full rounded-md border border-border bg-surface px-3 py-2 text-sm"
          >
            {memberAccessLevels.map((l) => (
              <option key={l} value={l}>
                {l.charAt(0) + l.slice(1).toLowerCase()}
              </option>
            ))}
          </select>

          <div className="flex gap-2">
            <Button
              size="sm"
              disabled={
                add.isPending ||
                (grantKind === "user" ? !userId : !teamId)
              }
              onClick={() => void submitAdd()}
            >
              {add.isPending ? "Adding…" : "Add"}
            </Button>
            <Button size="sm" variant="outline" onClick={() => setShowAdd(false)}>
              Cancel
            </Button>
          </div>
        </div>
      ) : null}

      {mailbox.members.length === 0 ? (
        <EmptyState
          icon={<Users className="size-8" />}
          title="No members yet"
          description="Add users or teams who should see this mailbox in the inbox."
        />
      ) : (
        <ul className="divide-y divide-border rounded-lg border border-border">
          {mailbox.members.map((member) => (
            <li key={member.id} className="flex items-center gap-3 px-4 py-3">
              <div className="min-w-0 flex-1">
                <div className="truncate text-sm font-medium">{member.name}</div>
                <div className="truncate text-xs text-text-muted">
                  {member.teamId ? "Team" : member.email ?? "User"}
                </div>
              </div>
              {member.teamId ? <Badge variant="outline">Team</Badge> : null}
              <select
                aria-label={`Access level for ${member.name}`}
                value={member.accessLevel}
                onChange={(e) =>
                  void changeLevel(member, e.target.value as MemberAccessLevel)
                }
                disabled={update.isPending}
                className="rounded-md border border-border bg-surface px-2 py-1 text-xs"
              >
                {memberAccessLevels.map((l) => (
                  <option key={l} value={l}>
                    {l.charAt(0) + l.slice(1).toLowerCase()}
                  </option>
                ))}
              </select>
              <Button
                variant="ghost"
                size="icon"
                aria-label={`Remove ${member.name}`}
                onClick={() => setConfirmRemove(member)}
              >
                <Trash2 className="size-4 text-destructive" />
              </Button>
            </li>
          ))}
        </ul>
      )}

      {confirmRemove ? (
        <ConfirmDialog
          title={`Remove ${confirmRemove.name}?`}
          hint="They will lose access to this mailbox immediately."
          busy={remove.isPending}
          onCancel={() => setConfirmRemove(undefined)}
          onConfirm={() => void doRemove()}
        />
      ) : null}
    </div>
  );
}
