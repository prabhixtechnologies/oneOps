import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest, apiRequestVoid } from "@/lib/api-client";
import {
  liveVisitorListSchema,
  pageViewListPageSchema,
  visitorAnalyticsSummarySchema,
  visitorDetailSchema,
  visitorListPageSchema,
  visitorEventListPageSchema,
} from "@/lib/schemas/visitor";

export function useLiveVisitors() {
  return useQuery({
    queryKey: ["visitors-live"],
    queryFn: () => apiRequest("/oneops/visitors/live", liveVisitorListSchema),
    refetchInterval: 15_000,
  });
}

export function useVisitors(search?: string) {
  return useInfiniteQuery({
    queryKey: ["visitors", search],
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams({ limit: "50" });
      if (pageParam) params.set("cursor", pageParam);
      if (search) params.set("search", search);
      return apiRequest(`/oneops/visitors?${params}`, visitorListPageSchema);
    },
    initialPageParam: null as string | null,
    getNextPageParam: (last) => (last.hasMore ? last.nextCursor ?? undefined : undefined),
  });
}

export function useVisitor(id: string | undefined) {
  return useQuery({
    queryKey: ["visitor", id],
    queryFn: () => apiRequest(`/oneops/visitors?id=${id}`, visitorDetailSchema),
    enabled: !!id,
  });
}

export function useVisitorPageViews(visitorId: string | undefined) {
  return useInfiniteQuery({
    queryKey: ["visitor-page-views", visitorId],
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams({ limit: "50" });
      if (pageParam) params.set("cursor", pageParam);
      return apiRequest(`/oneops/visitors/page-views?id=${visitorId}&${params}`, pageViewListPageSchema);
    },
    initialPageParam: null as string | null,
    getNextPageParam: (last) => (last.hasMore ? last.nextCursor ?? undefined : undefined),
    enabled: !!visitorId,
  });
}

export function useVisitorEvents(visitorId: string | undefined) {
  return useInfiniteQuery({
    queryKey: ["visitor-events", visitorId],
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams({ limit: "50" });
      if (pageParam) params.set("cursor", pageParam);
      return apiRequest(`/oneops/visitors/events?id=${visitorId}&${params}`, visitorEventListPageSchema);
    },
    initialPageParam: null as string | null,
    getNextPageParam: (last) => (last.hasMore ? last.nextCursor ?? undefined : undefined),
    enabled: !!visitorId,
  });
}

export function useVisitorAnalytics(days = 30) {
  return useQuery({
    queryKey: ["visitor-analytics", days],
    queryFn: () =>
      apiRequest(`/oneops/visitors/analytics/summary?days=${days}`, visitorAnalyticsSummarySchema),
  });
}

export function useDeleteVisitor() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiRequestVoid(`/oneops/visitors?id=${id}`, { method: "DELETE" }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["visitors"] });
      void qc.invalidateQueries({ queryKey: ["visitors-live"] });
    },
  });
}
