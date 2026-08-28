import { Pencil, Plus, Trash2, UserMinus } from "lucide-react";
import { useState } from "react";
import { useSearchParams } from "react-router";
import { toast } from "sonner";
import { PageHeader } from "@/components/shared/PageHeader";
import { CursorList } from "@/components/shared/CursorList";
import { MobileCard, MobileCardRow, ResponsiveTable } from "@/components/shared/ResponsiveTable";
import { RelativeTime } from "@/components/shared/RelativeTime";
import { ErrorState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type { Member, Team } from "@/lib/schemas/org";
import {
  useAddTeamMember,
  useChangeMemberRole,
  useCreateInvite,
  useCreateTeam,
  useDeleteTeam,
  useInvites,
  useMembers,
  usePermissions,
  useRemoveMember,
  useRemoveTeamMember,
  useResendInvite,
  useRevokeInvite,
  useRoles,
  useSuspendMember,
  useTeamMembers,
  useTeams,
  useUpdateRole,
  useUpdateTeam,
} from "@/features/org/api";
import { getApiErrorMessage } from "@/lib/api-client";

export default function MembersPage() {
  const [search, setSearch] = useState("");
  const [searchParams] = useSearchParams();
  const [inviteOpen, setInviteOpen] = useState(searchParams.get("invite") === "true");
  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteRoleId, setInviteRoleId] = useState("");

  const membersQuery = useMembers(search);
  const rolesQuery = useRoles();
  const permissionsQuery = usePermissions();
  const teamsQuery = useTeams();
  const invitesQuery = useInvites();
  const createInvite = useCreateInvite();
  const revokeInvite = useRevokeInvite();
  const resendInvite = useResendInvite();
  const updateRole = useUpdateRole();
  const changeMemberRole = useChangeMemberRole();
  const suspendMember = useSuspendMember();
  const removeMember = useRemoveMember();
  const createTeam = useCreateTeam();
  const updateTeam = useUpdateTeam();
  const deleteTeam = useDeleteTeam();
  const addTeamMember = useAddTeamMember();
  const removeTeamMember = useRemoveTeamMember();

  const [editingRoleId, setEditingRoleId] = useState<string | null>(null);
  const [selectedPerms, setSelectedPerms] = useState<Set<string>>(new Set());
  const [teamDialogOpen, setTeamDialogOpen] = useState(false);
  const [editingTeam, setEditingTeam] = useState<Team | null>(null);
  const [teamName, setTeamName] = useState("");
  const [teamDescription, setTeamDescription] = useState("");
  const [selectedTeamId, setSelectedTeamId] = useState<string | undefined>();
  const [addMemberUserId, setAddMemberUserId] = useState("");
  const [roleChangeMember, setRoleChangeMember] = useState<Member | null>(null);
  const [newRoleId, setNewRoleId] = useState("");

  const teamMembersQuery = useTeamMembers(selectedTeamId);
  const members = membersQuery.data?.pages.flatMap((p) => p.items) ?? [];

  const sendInvite = async () => {
    try {
      await createInvite.mutateAsync({ email: inviteEmail, roleId: inviteRoleId });
      toast.success(`Invitation sent to ${inviteEmail}`);
      setInviteOpen(false);
      setInviteEmail("");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  const openRoleEditor = (roleId: string, permissions: string[]) => {
    setEditingRoleId(roleId);
    setSelectedPerms(new Set(permissions));
  };

  const saveRole = async () => {
    if (!editingRoleId) return;
    try {
      await updateRole.mutateAsync({ id: editingRoleId, permissions: [...selectedPerms] });
      toast.success("Role permissions updated");
      setEditingRoleId(null);
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  const openTeamDialog = (team?: Team) => {
    setEditingTeam(team ?? null);
    setTeamName(team?.name ?? "");
    setTeamDescription(team?.description ?? "");
    setTeamDialogOpen(true);
  };

  const saveTeam = async () => {
    try {
      if (editingTeam) {
        await updateTeam.mutateAsync({ id: editingTeam.id, name: teamName, description: teamDescription });
        toast.success("Team updated");
      } else {
        await createTeam.mutateAsync({ name: teamName, description: teamDescription });
        toast.success("Team created");
      }
      setTeamDialogOpen(false);
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  const saveMemberRole = async () => {
    if (!roleChangeMember) return;
    try {
      await changeMemberRole.mutateAsync({ memberId: roleChangeMember.id, roleId: newRoleId });
      toast.success("Role updated");
      setRoleChangeMember(null);
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  if (rolesQuery.isError) {
    return <ErrorState message="Failed to load members" onRetry={() => void rolesQuery.refetch()} />;
  }

  return (
    <div className="space-y-6 p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] md:p-6">
      <PageHeader
        title="Members & roles"
        description="Manage team access, roles, and permissions"
        actions={
          <Button onClick={() => setInviteOpen(true)}>
            <Plus className="h-4 w-4" /> Invite member
          </Button>
        }
      />

      <Tabs defaultValue="members">
        <TabsList className="overflow-x-auto">
          <TabsTrigger value="members">Members</TabsTrigger>
          <TabsTrigger value="roles">Roles</TabsTrigger>
          <TabsTrigger value="teams">Teams</TabsTrigger>
          <TabsTrigger value="invites">Pending invites</TabsTrigger>
        </TabsList>

        <TabsContent value="members" className="mt-4">
          <Input
            placeholder="Search by name or email…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="mb-4 max-w-sm"
            aria-label="Search members"
          />
          <div className="hidden h-[500px] rounded-lg border border-border md:block">
            <CursorList<Member>
              items={members}
              hasMore={!!membersQuery.hasNextPage}
              isLoading={membersQuery.isLoading}
              isError={membersQuery.isError}
              isFetchingNextPage={membersQuery.isFetchingNextPage}
              onLoadMore={() => void membersQuery.fetchNextPage()}
              getKey={(m) => m.id}
              emptyTitle="No members found"
              estimateSize={56}
              renderItem={(m) => (
                <div className="flex items-center justify-between border-b border-border px-4 py-3 text-sm">
                  <div>
                    <p className="font-medium">{m.displayName}</p>
                    <p className="break-all text-text-muted">{m.email}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge variant="secondary">{m.roleName}</Badge>
                    {m.lastActiveAt && <RelativeTime date={m.lastActiveAt} className="text-xs" />}
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="sm">Actions</Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onSelect={() => { setRoleChangeMember(m); setNewRoleId(m.roleId); }}>
                          Change role
                        </DropdownMenuItem>
                        <DropdownMenuItem
                          onSelect={() =>
                            void suspendMember
                              .mutateAsync(m.id)
                              .then(() => toast.success("Member suspended"))
                              .catch((err) => toast.error(getApiErrorMessage(err)))
                          }
                        >
                          Suspend
                        </DropdownMenuItem>
                        <DropdownMenuItem
                          className="text-destructive"
                          onSelect={() =>
                            void removeMember
                              .mutateAsync(m.id)
                              .then(() => toast.success("Member removed"))
                              .catch((err) => toast.error(getApiErrorMessage(err)))
                          }
                        >
                          Remove
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </div>
                </div>
              )}
            />
          </div>
          <div className="h-[500px] md:hidden">
            <CursorList<Member>
              items={members}
              hasMore={!!membersQuery.hasNextPage}
              isLoading={membersQuery.isLoading}
              isError={membersQuery.isError}
              isFetchingNextPage={membersQuery.isFetchingNextPage}
              onLoadMore={() => void membersQuery.fetchNextPage()}
              getKey={(m) => m.id}
              emptyTitle="No members found"
              estimateSize={120}
              renderItem={(m) => (
                <MobileCard>
                  <p className="font-medium">{m.displayName}</p>
                  <p className="break-all text-text-muted">{m.email}</p>
                  <MobileCardRow label="Role" value={<Badge variant="secondary">{m.roleName}</Badge>} />
                  <div className="mt-3 flex flex-wrap gap-2">
                    <Button size="sm" variant="outline" onClick={() => { setRoleChangeMember(m); setNewRoleId(m.roleId); }}>Change role</Button>
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={suspendMember.isPending}
                      onClick={() =>
                        void suspendMember
                          .mutateAsync(m.id)
                          .then(() => toast.success("Member suspended"))
                          .catch((err) => toast.error(getApiErrorMessage(err)))
                      }
                    >
                      Suspend
                    </Button>
                    <Button
                      size="sm"
                      variant="destructive"
                      disabled={removeMember.isPending}
                      onClick={() =>
                        void removeMember
                          .mutateAsync(m.id)
                          .then(() => toast.success("Member removed"))
                          .catch((err) => toast.error(getApiErrorMessage(err)))
                      }
                    >
                      Remove
                    </Button>
                  </div>
                </MobileCard>
              )}
            />
          </div>
        </TabsContent>

        <TabsContent value="roles" className="mt-4">
          <ResponsiveTable
            mobile={
              rolesQuery.data?.items.map((role) => (
                <MobileCard key={role.id}>
                  <p className="font-medium">{role.name}</p>
                  <p className="text-xs text-text-muted">{role.description}</p>
                  <MobileCardRow label="Members" value={role.memberCount} />
                  <MobileCardRow label="Permissions" value={role.permissions.length} />
                  <Button className="mt-3 w-full" variant="outline" size="sm" onClick={() => openRoleEditor(role.id, role.permissions)}>
                    Edit permissions
                  </Button>
                </MobileCard>
              ))
            }
          >
            <div className="rounded-lg border border-border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Role</TableHead>
                    <TableHead>Members</TableHead>
                    <TableHead>Permissions</TableHead>
                    <TableHead />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {rolesQuery.data?.items.map((role) => (
                    <TableRow key={role.id}>
                      <TableCell>
                        <p className="font-medium">{role.name}</p>
                        <p className="text-xs text-text-muted">{role.description}</p>
                      </TableCell>
                      <TableCell>{role.memberCount}</TableCell>
                      <TableCell>{role.permissions.length}</TableCell>
                      <TableCell>
                        <Button variant="outline" size="sm" onClick={() => openRoleEditor(role.id, role.permissions)}>
                          Edit permissions
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </ResponsiveTable>
        </TabsContent>

        <TabsContent value="teams" className="mt-4 space-y-4">
          <Button onClick={() => openTeamDialog()}>
            <Plus className="h-4 w-4" /> Create team
          </Button>
          <div className="grid gap-4 sm:grid-cols-2">
            {teamsQuery.data?.items.map((team) => (
              <div key={team.id} className="rounded-lg border border-border p-4">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <h3 className="font-medium">{team.name}</h3>
                    <p className="text-sm text-text-muted">{team.description}</p>
                    <p className="mt-2 text-xs text-text-muted">{team.memberCount} members</p>
                  </div>
                  <div className="flex gap-1">
                    <Button variant="ghost" size="icon" aria-label={`Edit ${team.name}`} onClick={() => openTeamDialog(team)}>
                      <Pencil className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label={`Delete ${team.name}`}
                      disabled={deleteTeam.isPending}
                      onClick={() =>
                        void deleteTeam
                          .mutateAsync(team.id)
                          .then(() => toast.success("Team deleted"))
                          .catch((err) => toast.error(getApiErrorMessage(err)))
                      }
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
                <Button
                  variant="outline"
                  size="sm"
                  className="mt-3"
                  onClick={() => setSelectedTeamId(team.id)}
                >
                  Manage members
                </Button>
              </div>
            ))}
          </div>
        </TabsContent>

        <TabsContent value="invites" className="mt-4">
          <ResponsiveTable
            mobile={
              (invitesQuery.data?.items.length ?? 0) === 0 ? (
                <p className="py-8 text-center text-sm text-text-muted">No pending invites</p>
              ) : (
                invitesQuery.data?.items.map((inv) => (
                  <MobileCard key={inv.id}>
                    <p className="font-medium">{inv.email}</p>
                    <MobileCardRow label="Role" value={inv.roleName} />
                    <MobileCardRow label="Invited by" value={inv.invitedBy} />
                    <div className="mt-3 flex gap-2">
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={resendInvite.isPending}
                        onClick={() =>
                          void resendInvite
                            .mutateAsync(inv.id)
                            .then(() => toast.success("Invite resent"))
                            .catch((err) => toast.error(getApiErrorMessage(err)))
                        }
                      >
                        Resend
                      </Button>
                      <Button
                        size="sm"
                        variant="destructive"
                        disabled={revokeInvite.isPending}
                        onClick={() =>
                          void revokeInvite
                            .mutateAsync(inv.id)
                            .then(() => toast.success("Invite revoked"))
                            .catch((err) => toast.error(getApiErrorMessage(err)))
                        }
                      >
                        Revoke
                      </Button>
                    </div>
                  </MobileCard>
                ))
              )
            }
          >
            <ul className="divide-y divide-border rounded-lg border border-border">
              {(invitesQuery.data?.items.length ?? 0) === 0 ? (
                <li className="px-4 py-8 text-center text-sm text-text-muted">No pending invites</li>
              ) : (
                invitesQuery.data?.items.map((inv) => (
                  <li key={inv.id} className="flex items-center justify-between px-4 py-3 text-sm">
                    <div>
                      <p className="font-medium">{inv.email}</p>
                      <p className="text-text-muted">Role: {inv.roleName} · Invited by {inv.invitedBy}</p>
                    </div>
                    <div className="flex items-center gap-2">
                      <RelativeTime date={inv.createdAt} className="text-xs" />
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={resendInvite.isPending}
                        onClick={() =>
                          void resendInvite
                            .mutateAsync(inv.id)
                            .then(() => toast.success("Invite resent"))
                            .catch((err) => toast.error(getApiErrorMessage(err)))
                        }
                      >
                        Resend
                      </Button>
                      <Button
                        size="sm"
                        variant="destructive"
                        disabled={revokeInvite.isPending}
                        onClick={() =>
                          void revokeInvite
                            .mutateAsync(inv.id)
                            .then(() => toast.success("Invite revoked"))
                            .catch((err) => toast.error(getApiErrorMessage(err)))
                        }
                      >
                        Revoke
                      </Button>
                    </div>
                  </li>
                ))
              )}
            </ul>
          </ResponsiveTable>
        </TabsContent>
      </Tabs>

      <Dialog open={inviteOpen} onOpenChange={setInviteOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>Invite team member</DialogTitle></DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label>Email</Label>
              <Input type="email" value={inviteEmail} onChange={(e) => setInviteEmail(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>Role</Label>
              <Select value={inviteRoleId} onValueChange={setInviteRoleId}>
                <SelectTrigger><SelectValue placeholder="Select role" /></SelectTrigger>
                <SelectContent>
                  {rolesQuery.data?.items.map((r) => (
                    <SelectItem key={r.id} value={r.id}>{r.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <Button onClick={() => void sendInvite()} disabled={!inviteEmail || !inviteRoleId || createInvite.isPending}>
              Send invite
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!editingRoleId} onOpenChange={() => setEditingRoleId(null)}>
        <DialogContent className="max-w-2xl">
          <DialogHeader><DialogTitle>Permission matrix</DialogTitle></DialogHeader>
          <div className="max-h-[min(50dvh,24rem)] overflow-auto">
            <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Permission</TableHead>
                  <TableHead>Category</TableHead>
                  <TableHead>Granted</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {permissionsQuery.data?.flatMap((cat) =>
                  cat.permissions.map((perm) => (
                    <TableRow key={perm.code}>
                      <TableCell className="font-mono text-xs">{perm.code}</TableCell>
                      <TableCell>{cat.category}</TableCell>
                      <TableCell>
                        <Checkbox
                          checked={selectedPerms.has(perm.code)}
                          onCheckedChange={(c) => {
                            setSelectedPerms((prev) => {
                              const next = new Set(prev);
                              if (c) next.add(perm.code);
                              else next.delete(perm.code);
                              return next;
                            });
                          }}
                          aria-label={`Grant ${perm.code}`}
                        />
                      </TableCell>
                    </TableRow>
                  )),
                )}
              </TableBody>
            </Table>
            </div>
          </div>
          <DialogFooter>
            <Button onClick={() => void saveRole()} disabled={updateRole.isPending}>Save permissions</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={teamDialogOpen} onOpenChange={setTeamDialogOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>{editingTeam ? "Rename team" : "Create team"}</DialogTitle></DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label>Name</Label>
              <Input value={teamName} onChange={(e) => setTeamName(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>Description</Label>
              <Input value={teamDescription} onChange={(e) => setTeamDescription(e.target.value)} />
            </div>
          </div>
          <DialogFooter>
            <Button onClick={() => void saveTeam()} disabled={!teamName || createTeam.isPending || updateTeam.isPending}>
              {editingTeam ? "Save" : "Create"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!selectedTeamId} onOpenChange={() => setSelectedTeamId(undefined)}>
        <DialogContent>
          <DialogHeader><DialogTitle>Team members</DialogTitle></DialogHeader>
          <ul className="max-h-48 space-y-2 overflow-auto">
            {teamMembersQuery.data?.items.map((tm) => (
              <li key={tm.userId} className="flex items-center justify-between text-sm">
                <span>{tm.displayName} ({tm.email})</span>
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label={`Remove ${tm.displayName}`}
                  disabled={removeTeamMember.isPending}
                  onClick={() =>
                    selectedTeamId &&
                    void removeTeamMember
                      .mutateAsync({ teamId: selectedTeamId, userId: tm.userId })
                      .then(() => toast.success("Member removed from team"))
                      .catch((err) => toast.error(getApiErrorMessage(err)))
                  }
                >
                  <UserMinus className="h-4 w-4" />
                </Button>
              </li>
            ))}
          </ul>
          <div className="flex gap-2">
            <Select value={addMemberUserId} onValueChange={setAddMemberUserId}>
              <SelectTrigger className="flex-1"><SelectValue placeholder="Add member" /></SelectTrigger>
              <SelectContent>
                {members.map((m) => (
                  <SelectItem key={m.userId} value={m.userId}>{m.displayName}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button
              disabled={!addMemberUserId || !selectedTeamId || addTeamMember.isPending}
              onClick={() =>
                selectedTeamId &&
                void addTeamMember
                  .mutateAsync({ teamId: selectedTeamId, userId: addMemberUserId })
                  .then(() => {
                    toast.success("Member added to team");
                    setAddMemberUserId("");
                  })
                  .catch((err) => toast.error(getApiErrorMessage(err)))
              }
            >
              Add
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={!!roleChangeMember} onOpenChange={() => setRoleChangeMember(null)}>
        <DialogContent>
          <DialogHeader><DialogTitle>Change role for {roleChangeMember?.displayName}</DialogTitle></DialogHeader>
          <Select value={newRoleId} onValueChange={setNewRoleId}>
            <SelectTrigger><SelectValue /></SelectTrigger>
            <SelectContent>
              {rolesQuery.data?.items.map((r) => (
                <SelectItem key={r.id} value={r.id}>{r.name}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <DialogFooter>
            <Button onClick={() => void saveMemberRole()} disabled={changeMemberRole.isPending}>Save</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
