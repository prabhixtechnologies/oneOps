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
  fetchMobiFlags,
  fetchMobiLive,
  fetchMobiPlans,
  fetchMobiReleases,
  fetchMobiSupport,
  kickMobiUser,
  replyMobiSupport,
  resolveMobiSupport,
  updateMobiRelease,
  upsertMobiFlag,
} from "@/features/ops/control-plane-api";
import { boolField, field } from "@/features/ops/json";

export default function MobiStackOpsPage() {
  const [tab, setTab] = useState("live");
  const qc = useQueryClient();
  const live = useQuery({ queryKey: ["mobi-live"], queryFn: fetchMobiLive, retry: 1 });
  const flags = useQuery({
    queryKey: ["mobi-flags"],
    queryFn: fetchMobiFlags,
    enabled: tab === "flags",
    retry: 1,
  });
  const releases = useQuery({
    queryKey: ["mobi-releases"],
    queryFn: fetchMobiReleases,
    enabled: tab === "releases",
    retry: 1,
  });
  const support = useQuery({
    queryKey: ["mobi-support"],
    queryFn: () => fetchMobiSupport(),
    enabled: tab === "support",
    retry: 1,
  });
  const plans = useQuery({
    queryKey: ["mobi-plans"],
    queryFn: fetchMobiPlans,
    enabled: tab === "plans",
    retry: 1,
  });

  const [kickTarget, setKickTarget] = useState<{ userId: string; deviceId?: string } | null>(null);
  const [reply, setReply] = useState<Record<string, string>>({});
  const [floors, setFloors] = useState<Record<string, string>>({});

  const kick = useMutation({
    mutationFn: ({ userId, deviceId, reason }: { userId: string; deviceId?: string; reason?: string }) =>
      kickMobiUser(userId, deviceId, reason),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["mobi-live"] });
      setKickTarget(null);
    },
  });

  const flagMut = useMutation({
    mutationFn: ({ code, enabled }: { code: string; enabled: boolean }) => upsertMobiFlag(code, enabled),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["mobi-flags"] }),
  });

  const releaseMut = useMutation({
    mutationFn: ({ platform, minNativeBuild }: { platform: string; minNativeBuild: number }) =>
      updateMobiRelease(platform, { minNativeBuild }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["mobi-releases"] }),
  });

  const replyMut = useMutation({
    mutationFn: ({ id, message }: { id: string; message: string }) => replyMobiSupport(id, message),
    onSuccess: (_, vars) => {
      void qc.invalidateQueries({ queryKey: ["mobi-support"] });
      setReply((prev) => ({ ...prev, [vars.id]: "" }));
    },
  });

  const resolveMut = useMutation({
    mutationFn: (id: string) => resolveMobiSupport(id),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["mobi-support"] }),
  });

  return (
    <div className="space-y-6 p-4 md:p-6">
      <PageHeader
        title="MobiStack ops"
        description="Live users, kick, release floor, flags, support and plans — via the oneOps BFF"
      />

      <Tabs value={tab} onValueChange={setTab}>
        <TabsList className="w-full justify-start overflow-x-auto">
          <TabsTrigger value="live">Live users</TabsTrigger>
          <TabsTrigger value="releases">Releases</TabsTrigger>
          <TabsTrigger value="flags">Flags</TabsTrigger>
          <TabsTrigger value="support">Support</TabsTrigger>
          <TabsTrigger value="plans">Plans</TabsTrigger>
        </TabsList>

        <TabsContent value="live" className="mt-6 space-y-3">
          {live.isError && (
            <ErrorState
              message="Failed to load live users. SUPPORT role is required."
              onRetry={() => void live.refetch()}
            />
          )}
          {live.isLoading && <Skeleton className="h-24" />}
          <div className="divide-y rounded-lg border border-border">
            {(live.data ?? []).map((row) => {
              const userId = field(row, "userId");
              return (
                <div key={`${userId}-${field(row, "deviceId")}`} className="flex items-center justify-between gap-3 px-4 py-3">
                  <div>
                    <div className="font-medium">{field(row, "fullName", "email") || userId}</div>
                    <div className="text-xs text-text-muted">
                      {[
                        field(row, "shopName"),
                        field(row, "platform"),
                        field(row, "appVersion"),
                        field(row, "deviceId"),
                        field(row, "seenAt"),
                      ]
                        .filter(Boolean)
                        .join(" · ")}
                    </div>
                  </div>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => setKickTarget({ userId, deviceId: field(row, "deviceId") || undefined })}
                  >
                    Kick
                  </Button>
                </div>
              );
            })}
          </div>
        </TabsContent>

        <TabsContent value="releases" className="mt-6 space-y-3">
          {releases.isError && (
            <ErrorState message="Failed to load releases" onRetry={() => void releases.refetch()} />
          )}
          <div className="divide-y rounded-lg border border-border">
            {(releases.data ?? []).map((row) => {
              const platform = field(row, "platform");
              return (
                <div key={platform} className="flex flex-col gap-2 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <div className="font-medium">{platform}</div>
                    <div className="text-xs text-text-muted">
                      min {field(row, "minNativeBuild")} · latest {field(row, "latestNativeBuild")}
                      {boolField(row, "forceNativeUpdate") ? " · force" : ""}
                    </div>
                  </div>
                  <div className="flex gap-2">
                    <Input
                      className="w-28"
                      value={floors[platform] ?? field(row, "minNativeBuild")}
                      onChange={(event) =>
                        setFloors((prev) => ({ ...prev, [platform]: event.target.value }))
                      }
                      aria-label={`${platform} min build`}
                    />
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={releaseMut.isPending}
                      onClick={() =>
                        releaseMut.mutate({
                          platform,
                          minNativeBuild: Number(floors[platform] ?? field(row, "minNativeBuild")),
                        })
                      }
                    >
                      Set floor
                    </Button>
                  </div>
                </div>
              );
            })}
          </div>
        </TabsContent>

        <TabsContent value="flags" className="mt-6 space-y-3">
          {flags.isError && (
            <ErrorState message="Failed to load flags" onRetry={() => void flags.refetch()} />
          )}
          <div className="divide-y rounded-lg border border-border">
            {(flags.data ?? []).map((row) => {
              const code = field(row, "code");
              const enabled = boolField(row, "enabled");
              return (
                <div key={code} className="flex items-center justify-between gap-3 px-4 py-3">
                  <div className="font-medium">{code}</div>
                  <div className="flex items-center gap-2">
                    <Badge variant={enabled ? "default" : "secondary"}>
                      {enabled ? "ON" : "OFF"}
                    </Badge>
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={flagMut.isPending}
                      onClick={() => flagMut.mutate({ code, enabled: !enabled })}
                    >
                      Toggle
                    </Button>
                  </div>
                </div>
              );
            })}
          </div>
        </TabsContent>

        <TabsContent value="support" className="mt-6 space-y-3">
          {support.isError && (
            <ErrorState message="Failed to load support" onRetry={() => void support.refetch()} />
          )}
          <div className="divide-y rounded-lg border border-border">
            {(support.data ?? []).map((row) => {
              const id = field(row, "id");
              return (
                <div key={id} className="space-y-2 px-4 py-3">
                  <div className="flex items-center justify-between gap-3">
                    <div>
                      <div className="font-medium">{field(row, "subject") || id}</div>
                      <div className="text-xs text-text-muted">
                        {[field(row, "userName"), field(row, "status"), field(row, "lastMessageAt")]
                          .filter(Boolean)
                          .join(" · ")}
                      </div>
                    </div>
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={resolveMut.isPending}
                      onClick={() => resolveMut.mutate(id)}
                    >
                      Resolve
                    </Button>
                  </div>
                  <div className="flex gap-2">
                    <Input
                      value={reply[id] ?? ""}
                      onChange={(event) =>
                        setReply((prev) => ({ ...prev, [id]: event.target.value }))
                      }
                      placeholder="Reply"
                    />
                    <Button
                      size="sm"
                      disabled={replyMut.isPending || !(reply[id] ?? "").trim()}
                      onClick={() => replyMut.mutate({ id, message: (reply[id] ?? "").trim() })}
                    >
                      Send
                    </Button>
                  </div>
                </div>
              );
            })}
          </div>
        </TabsContent>

        <TabsContent value="plans" className="mt-6 space-y-3">
          {plans.isError && (
            <ErrorState
              message="Failed to load plans. BILLING role is required."
              onRetry={() => void plans.refetch()}
            />
          )}
          <div className="divide-y rounded-lg border border-border">
            {(plans.data ?? []).map((row) => (
              <div key={field(row, "id")} className="flex items-center justify-between gap-3 px-4 py-3">
                <div>
                  <div className="font-medium">{field(row, "name", "code")}</div>
                  <div className="text-xs text-text-muted">
                    {[field(row, "amount"), field(row, "currency"), field(row, "interval")]
                      .filter(Boolean)
                      .join(" · ")}
                  </div>
                </div>
                <Badge variant={boolField(row, "active") ? "default" : "secondary"}>
                  {boolField(row, "active") ? "ACTIVE" : "OFF"}
                </Badge>
              </div>
            ))}
          </div>
        </TabsContent>
      </Tabs>

      <ReasonDialog
        open={kickTarget != null}
        title="Kick user"
        confirmLabel="Kick"
        pending={kick.isPending}
        onOpenChange={(open) => {
          if (!open) setKickTarget(null);
        }}
        onConfirm={(reason) => {
          if (kickTarget) kick.mutate({ ...kickTarget, reason });
        }}
      />
    </div>
  );
}
