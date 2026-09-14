import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { PageHeader } from "@/components/shared/PageHeader";
import { ErrorState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { ReasonDialog } from "@/features/ops/ReasonDialog";
import {
  breakGlassRevoke,
  fetchStaffGrants,
  grantStaffRole,
  revokeStaffRole,
} from "@/features/ops/control-plane-api";
import { STAFF_ROLES } from "@/lib/schemas/control-plane";

export default function StaffPage() {
  const qc = useQueryClient();
  const grants = useQuery({ queryKey: ["staff-grants"], queryFn: fetchStaffGrants, retry: 1 });
  const [userId, setUserId] = useState("");
  const [role, setRole] = useState("SUPPORT");
  const [note, setNote] = useState("");
  const [glassUser, setGlassUser] = useState<string | null>(null);
  const [revokeTarget, setRevokeTarget] = useState<{ userId: string; role: string } | null>(null);

  const grant = useMutation({
    mutationFn: () => grantStaffRole(userId.trim(), role, note.trim() || undefined),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["staff-grants"] });
      void qc.invalidateQueries({ queryKey: ["platform-staff-me"] });
      setNote("");
    },
  });

  const revoke = useMutation({
    mutationFn: ({ userId: id, role: r, note: n }: { userId: string; role: string; note?: string }) =>
      revokeStaffRole(id, r, n),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["staff-grants"] });
      setRevokeTarget(null);
    },
  });

  const glass = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => breakGlassRevoke(id, reason),
    onSuccess: () => setGlassUser(null),
  });

  return (
    <div className="space-y-6 p-4 md:p-6">
      <PageHeader
        title="Staff"
        description="Grant and revoke platform roles. Break-glass revokes every token for one account — OWNER / SECURITY only, and a reason is required."
      />

      {grants.isError && (
        <ErrorState
          message="Failed to load grants. OWNER role is required."
          onRetry={() => void grants.refetch()}
        />
      )}

      <section className="space-y-3 rounded-lg border border-border p-4">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-text-muted">Grant</h2>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <div className="space-y-1">
            <Label htmlFor="staff-user">User id</Label>
            <Input
              id="staff-user"
              value={userId}
              onChange={(event) => setUserId(event.target.value)}
              placeholder="UUID"
            />
          </div>
          <div className="space-y-1">
            <Label>Role</Label>
            <Select value={role} onValueChange={setRole}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {STAFF_ROLES.map((item) => (
                  <SelectItem key={item} value={item}>
                    {item}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1">
            <Label htmlFor="staff-note">Note</Label>
            <Input id="staff-note" value={note} onChange={(event) => setNote(event.target.value)} />
          </div>
          <div className="flex items-end">
            <Button
              disabled={grant.isPending || userId.trim().length === 0}
              onClick={() => grant.mutate()}
            >
              Grant
            </Button>
          </div>
        </div>
        {grant.isError && (
          <p className="text-sm text-destructive">
            {grant.error instanceof Error ? grant.error.message : "Grant failed"}
          </p>
        )}
      </section>

      <section className="space-y-3">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-text-muted">Grants</h2>
        {grants.isLoading && <Skeleton className="h-24" />}
        <div className="divide-y rounded-lg border border-border">
          {(grants.data ?? []).map((row) => (
            <div key={row.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div>
                <div className="font-medium">{row.userId}</div>
                <div className="text-xs text-text-muted">
                  {[row.grantedAt, row.note].filter(Boolean).join(" · ")}
                </div>
              </div>
              <div className="flex items-center gap-2">
                <Badge variant="secondary">{row.role}</Badge>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => setRevokeTarget({ userId: row.userId, role: row.role })}
                >
                  Revoke
                </Button>
                <Button size="sm" variant="destructive" onClick={() => setGlassUser(row.userId)}>
                  Break glass
                </Button>
              </div>
            </div>
          ))}
        </div>
      </section>

      <ReasonDialog
        open={revokeTarget != null}
        title="Revoke role"
        confirmLabel="Revoke"
        pending={revoke.isPending}
        onOpenChange={(open) => {
          if (!open) setRevokeTarget(null);
        }}
        onConfirm={(reason) => {
          if (revokeTarget) {
            revoke.mutate({ ...revokeTarget, note: reason });
          }
        }}
      />
      <ReasonDialog
        open={glassUser != null}
        title="Break glass"
        description="Invalidates every token and session for this account immediately."
        confirmLabel="Revoke tokens"
        required
        pending={glass.isPending}
        onOpenChange={(open) => {
          if (!open) setGlassUser(null);
        }}
        onConfirm={(reason) => {
          if (glassUser) glass.mutate({ id: glassUser, reason });
        }}
      />
    </div>
  );
}
