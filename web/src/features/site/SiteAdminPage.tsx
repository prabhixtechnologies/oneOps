import { PageHeader } from "@/components/shared/PageHeader";
import { ErrorState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { apiRequest } from "@/lib/api-client";
import { useQuery } from "@tanstack/react-query";
import { z } from "zod";

const jobRoleSchema = z.object({
  slug: z.string(),
  title: z.string(),
  department: z.string().nullable().optional(),
  location: z.string().nullable().optional(),
  employmentType: z.string().nullable().optional(),
});

export default function SiteAdminPage() {
  const careersQuery = useQuery({
    queryKey: ["site-careers"],
    queryFn: () => apiRequest("/site/careers", z.array(jobRoleSchema), { skipAuth: true }),
  });

  return (
    <div className="space-y-6 p-4 md:p-6">
      <PageHeader
        title="Site admin"
        description="Marketing site content. List endpoints for leads, subscribers, and job applications are not exposed — only open roles can be browsed."
      />

      <section>
        <h2 className="mb-3 text-lg font-semibold">Open roles</h2>
        {careersQuery.isLoading ? (
          <Skeleton className="h-32" />
        ) : careersQuery.isError ? (
          <ErrorState message="Failed to load careers" onRetry={() => void careersQuery.refetch()} />
        ) : (careersQuery.data ?? []).length === 0 ? (
          <p className="text-sm text-text-muted">No open roles published.</p>
        ) : (
          <div className="grid gap-3 md:grid-cols-2">
            {(careersQuery.data ?? []).map((role) => (
              <div key={role.slug} className="rounded-lg border border-border p-4">
                <p className="font-medium">{role.title}</p>
                <div className="mt-2 flex flex-wrap gap-2">
                  {role.department && <Badge variant="outline">{role.department}</Badge>}
                  {role.location && <Badge variant="secondary">{role.location}</Badge>}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      <section className="rounded-lg border border-dashed border-border p-6 text-sm text-text-muted">
        <p className="font-medium text-text">Unavailable in API</p>
        <ul className="mt-2 list-inside list-disc space-y-1">
          <li>GET list of marketing leads (`/site/leads` is POST-only)</li>
          <li>GET list of newsletter subscribers</li>
          <li>GET list of job applications</li>
        </ul>
      </section>
    </div>
  );
}
