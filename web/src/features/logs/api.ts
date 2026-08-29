import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { apiDownload, apiRequest, triggerBlobDownload } from "@/lib/api-client";
import {
  eventLogPageSchema,
  eventLogSchema,
  eventLogStatsSchema,
  traceViewSchema,
} from "@/lib/schemas/logs";

export interface EventLogFilters {
  severity?: string;
  category?: string;
  eventCode?: string;
  correlationId?: string;
  search?: string;
  from?: string;
  to?: string;
  /**
   * Search every organization rather than the current one. Platform staff only; the API refuses it
   * for anyone else rather than quietly narrowing, so a wrong answer is not possible.
   */
  allOrganizations?: boolean;
}

function buildParams(filters: EventLogFilters, cursor?: string | null, limit = 50) {
  const params = new URLSearchParams({ limit: String(limit) });
  if (cursor) params.set("cursor", cursor);
  if (filters.severity) params.set("severity", filters.severity);
  if (filters.category) params.set("category", filters.category);
  if (filters.eventCode) params.set("eventCode", filters.eventCode);
  if (filters.correlationId) params.set("correlationId", filters.correlationId);
  if (filters.search) params.set("search", filters.search);
  if (filters.from) params.set("from", filters.from);
  if (filters.to) params.set("to", filters.to);
  // Only sent when true: the API rejects naming an organization and all of them together, and the
  // client always sends the org header.
  if (filters.allOrganizations) params.set("allOrganizations", "true");
  return params;
}

export function useEventLogs(filters: EventLogFilters) {
  return useInfiniteQuery({
    queryKey: ["event-logs", filters],
    queryFn: ({ pageParam }) =>
      apiRequest(`/event-logs?${buildParams(filters, pageParam)}`, eventLogPageSchema, {
        // The org header would contradict allOrganizations and be refused, so it is dropped for the
        // cross-organization sweep and sent as usual otherwise.
        skipOrg: filters.allOrganizations,
      }),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => (last.hasMore ? last.nextCursor : undefined),
  });
}

export function useEventLog(id: string | undefined, allOrganizations = false) {
  return useQuery({
    queryKey: ["event-log", id, allOrganizations],
    queryFn: () =>
      apiRequest(
        `/event-logs/${id}${allOrganizations ? "?allOrganizations=true" : ""}`,
        eventLogSchema,
        { skipOrg: allOrganizations },
      ),
    enabled: !!id,
  });
}

export function useEventLogStats(from?: string, to?: string, allOrganizations = false) {
  const params = new URLSearchParams();
  if (from) params.set("from", from);
  if (to) params.set("to", to);
  if (allOrganizations) params.set("allOrganizations", "true");
  const qs = params.toString();
  return useQuery({
    queryKey: ["event-log-stats", from, to, allOrganizations],
    queryFn: () =>
      apiRequest(`/event-logs/stats${qs ? `?${qs}` : ""}`, eventLogStatsSchema, {
        skipOrg: allOrganizations,
      }),
  });
}

export function useEventLogTrace(correlationId: string | undefined, allOrganizations = false) {
  return useQuery({
    queryKey: ["event-log-trace", correlationId, allOrganizations],
    queryFn: () =>
      apiRequest(
        `/event-logs/trace/${correlationId}${allOrganizations ? "?allOrganizations=true" : ""}`,
        traceViewSchema,
        { skipOrg: allOrganizations },
      ),
    enabled: !!correlationId,
  });
}

export async function exportEventLogs(format: "csv" | "ndjson", filters: EventLogFilters) {
  const params = buildParams(filters);
  params.set("format", format);
  const { blob, filename } = await apiDownload(`/event-logs/export?${params}`, {
    skipOrg: filters.allOrganizations,
  });
  triggerBlobDownload(blob, filename);
}
