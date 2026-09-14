import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { PageHeader } from "@/components/shared/PageHeader";
import { ErrorState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ReasonDialog } from "@/features/ops/ReasonDialog";
import {
  fetchIdentityClients,
  fetchIdentityEvents,
  fetchIdentityKeys,
  fetchIdentityUser,
  identityUserAction,
  searchIdentityUsers,
} from "@/features/ops/control-plane-api";
import { asRecord, asRecords, boolField, field } from "@/features/ops/json";

export default function IdentityPage() {
  const [tab, setTab] = useState("users");
  const [q, setQ] = useState("");
  const [submitted, setSubmitted] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [action, setAction] = useState<
    "disable" | "enable" | "unlock" | "force-reset" | "revoke-sessions" | null
  >(null);

  const qc = useQueryClient();
  const users = useQuery({
    queryKey: ["identity-users", submitted],
    queryFn: () => searchIdentityUsers({ q: submitted || undefined }),
    retry: 1,
  });
  const detail = useQuery({
    queryKey: ["identity-user", selectedId],
    queryFn: () => fetchIdentityUser(selectedId!),
    enabled: !!selectedId,
    retry: 1,
  });
  const clients = useQuery({
    queryKey: ["identity-clients"],
    queryFn: fetchIdentityClients,
    enabled: tab === "clients",
    retry: 1,
  });
  const keys = useQuery({
    queryKey: ["identity-keys"],
    queryFn: fetchIdentityKeys,
    enabled: tab === "keys",
    retry: 1,
  });
  const events = useQuery({
    queryKey: ["identity-events"],
    queryFn: () => fetchIdentityEvents(),
    enabled: tab === "events",
    retry: 1,
  });

  const mutate = useMutation({
    mutationFn: ({
      id,
      kind,
      reason,
    }: {
      id: string;
      kind: NonNullable<typeof action>;
      reason?: string;
    }) => identityUserAction(id, kind, reason),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["identity-users"] });
      void qc.invalidateQueries({ queryKey: ["identity-user", selectedId] });
      setAction(null);
    },
  });

  const user = asRecord(asRecord(detail.data).user);
  const sessions = asRecords(asRecord(detail.data).sessions);

  return (
    <div className="space-y-6 p-4 md:p-6">
      <PageHeader
        title="Identity"
        description="Users, sessions, clients, signing keys and security events — Identity is never called from the browser"
      />

      <Tabs value={tab} onValueChange={setTab}>
        <TabsList className="w-full justify-start overflow-x-auto">
          <TabsTrigger value="users">Users</TabsTrigger>
          <TabsTrigger value="clients">Clients</TabsTrigger>
          <TabsTrigger value="keys">Keys</TabsTrigger>
          <TabsTrigger value="events">Events</TabsTrigger>
        </TabsList>

        <TabsContent value="users" className="mt-6 space-y-4">
          <form
            className="flex gap-2"
            onSubmit={(event) => {
              event.preventDefault();
              setSubmitted(q.trim());
            }}
          >
            <Input
              value={q}
              onChange={(event) => setQ(event.target.value)}
              placeholder="Search email or name"
              aria-label="Search users"
            />
            <Button type="submit">Search</Button>
          </form>
          {users.isError && (
            <ErrorState
              message="Failed to load users. SECURITY role is required."
              onRetry={() => void users.refetch()}
            />
          )}
          {users.isLoading && <Skeleton className="h-24" />}
          <div className="grid gap-4 lg:grid-cols-2">
            <div className="divide-y rounded-lg border border-border">
              {(users.data?.items ?? []).map((row) => {
                const id = field(row, "id");
                return (
                  <button
                    key={id}
                    type="button"
                    className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left hover:bg-surface"
                    onClick={() => setSelectedId(id)}
                  >
                    <div>
                      <div className="font-medium">{field(row, "email")}</div>
                      <div className="text-xs text-text-muted">
                        {[field(row, "fullName"), field(row, "status")].filter(Boolean).join(" · ")}
                      </div>
                    </div>
                    <div className="flex gap-1">
                      {boolField(row, "locked") && <Badge variant="destructive">LOCKED</Badge>}
                      {boolField(row, "emailVerified") && <Badge variant="secondary">VERIFIED</Badge>}
                    </div>
                  </button>
                );
              })}
            </div>
            <div className="rounded-lg border border-border p-4">
              {!selectedId && <p className="text-sm text-text-muted">Select a user.</p>}
              {detail.isLoading && <Skeleton className="h-24" />}
              {selectedId && detail.isSuccess && (
                <div className="space-y-3">
                  <div>
                    <div className="font-medium">{field(user, "email")}</div>
                    <div className="text-xs text-text-muted">
                      {[field(user, "fullName"), field(user, "status"), field(user, "id")]
                        .filter(Boolean)
                        .join(" · ")}
                    </div>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <Button size="sm" variant="outline" onClick={() => setAction("disable")}>
                      Disable
                    </Button>
                    <Button size="sm" variant="outline" onClick={() => setAction("enable")}>
                      Enable
                    </Button>
                    <Button size="sm" variant="outline" onClick={() => setAction("unlock")}>
                      Unlock
                    </Button>
                    <Button size="sm" variant="outline" onClick={() => setAction("force-reset")}>
                      Force reset
                    </Button>
                    <Button size="sm" variant="destructive" onClick={() => setAction("revoke-sessions")}>
                      Revoke sessions
                    </Button>
                  </div>
                  <h3 className="text-sm font-semibold uppercase tracking-wide text-text-muted">
                    Sessions
                  </h3>
                  <div className="divide-y rounded-md border border-border">
                    {sessions.map((session) => (
                      <div key={field(session, "id")} className="px-3 py-2 text-xs text-text-muted">
                        {[
                          field(session, "clientId"),
                          field(session, "ipAddress"),
                          field(session, "lastSeenAt", "createdAt"),
                        ]
                          .filter(Boolean)
                          .join(" · ")}
                      </div>
                    ))}
                    {sessions.length === 0 && (
                      <div className="px-3 py-2 text-xs text-text-muted">No live sessions.</div>
                    )}
                  </div>
                </div>
              )}
            </div>
          </div>
        </TabsContent>

        <TabsContent value="clients" className="mt-6">
          {clients.isError && (
            <ErrorState message="Failed to load clients" onRetry={() => void clients.refetch()} />
          )}
          <RecordList rows={clients.data ?? []} titleKeys={["name", "clientId"]} />
        </TabsContent>

        <TabsContent value="keys" className="mt-6">
          {keys.isError && (
            <ErrorState message="Failed to load keys" onRetry={() => void keys.refetch()} />
          )}
          <RecordList rows={keys.data ?? []} titleKeys={["keyId", "algorithm"]} />
        </TabsContent>

        <TabsContent value="events" className="mt-6">
          {events.isError && (
            <ErrorState message="Failed to load events" onRetry={() => void events.refetch()} />
          )}
          <RecordList rows={events.data?.items ?? []} titleKeys={["type", "email", "outcome"]} />
        </TabsContent>
      </Tabs>

      <ReasonDialog
        open={action != null}
        title={action ? action.replace(/-/g, " ") : ""}
        description="Recorded next to the Identity audit event."
        confirmLabel="Confirm"
        pending={mutate.isPending}
        onOpenChange={(open) => {
          if (!open) setAction(null);
        }}
        onConfirm={(reason) => {
          if (selectedId && action) {
            mutate.mutate({ id: selectedId, kind: action, reason });
          }
        }}
      />
    </div>
  );
}

function RecordList({
  rows,
  titleKeys,
}: {
  rows: Record<string, unknown>[];
  titleKeys: string[];
}) {
  return (
    <div className="divide-y rounded-lg border border-border">
      {rows.map((row, index) => (
        <div key={field(row, "id", "clientId", "keyId") || String(index)} className="px-4 py-3">
          <div className="font-medium">{titleKeys.map((key) => field(row, key)).filter(Boolean).join(" · ")}</div>
          <div className="text-xs text-text-muted">
            {["status", "algorithm", "current", "occurredAt", "ipAddress", "userAgent"]
              .map((key) => field(row, key))
              .filter(Boolean)
              .join(" · ")}
          </div>
        </div>
      ))}
    </div>
  );
}
