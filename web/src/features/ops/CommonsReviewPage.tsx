import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { PageHeader } from "@/components/shared/PageHeader";
import { ErrorState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { fetchCommonsQueue, reviewCommons } from "@/features/ops/control-plane-api";
import { asRecords, field } from "@/features/ops/json";

export default function CommonsReviewPage() {
  const qc = useQueryClient();
  const queue = useQuery({ queryKey: ["commons-queue"], queryFn: fetchCommonsQueue, retry: 1 });
  const rows = asRecords(queue.data);

  const review = useMutation({
    mutationFn: ({ id, decision }: { id: string; decision: "accept" | "reject" }) =>
      reviewCommons(id, decision),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["commons-queue"] }),
  });

  return (
    <div className="space-y-6 p-4 md:p-6">
      <PageHeader
        title="Commons review"
        description="Catalog contributions waiting on a reviewer. Accept and reject go through the oneOps BFF."
      />

      {queue.isError && (
        <ErrorState
          message="Failed to load the commons queue. SUPPORT role is required."
          onRetry={() => void queue.refetch()}
        />
      )}
      {queue.isLoading && <Skeleton className="h-24" />}

      <div className="divide-y rounded-lg border border-border">
        {rows.map((row) => {
          const id = field(row, "id");
          return (
            <div key={id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div>
                <div className="font-medium">
                  {[field(row, "kind"), field(row, "reason")].filter(Boolean).join(" · ") || id}
                </div>
                <div className="text-xs text-text-muted">
                  {[field(row, "submittedBy"), field(row, "createdAt"), field(row, "targetId")]
                    .filter(Boolean)
                    .join(" · ")}
                </div>
              </div>
              <div className="flex items-center gap-2">
                <Badge variant="secondary">{field(row, "status") || "PENDING"}</Badge>
                <Button
                  size="sm"
                  disabled={review.isPending}
                  onClick={() => review.mutate({ id, decision: "accept" })}
                >
                  Accept
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  disabled={review.isPending}
                  onClick={() => review.mutate({ id, decision: "reject" })}
                >
                  Reject
                </Button>
              </div>
            </div>
          );
        })}
        {!queue.isLoading && rows.length === 0 && (
          <div className="px-4 py-6 text-sm text-text-muted">Nothing in the queue.</div>
        )}
      </div>
    </div>
  );
}
