import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchStaffMe } from "@/features/ops/control-plane-api";
import { apiRequest } from "@/lib/api-client";
import { IS_ADMIN_APP } from "@/lib/app-mode";
import {
  applicationDetailSchema,
  applicationPageSchema,
  leadDetailSchema,
  leadPageSchema,
  platformOverviewSchema,
  subscriberPageSchema,
  tenantPageSchema,
} from "@/lib/schemas/ops";

/**
 * Hooks for the platform operator hub.
 *
 * <p>These endpoints are cross-tenant, so every request carries no org header and the server
 * authorises on PLATFORM_ADMIN alone. A non-admin who reaches this code gets a 403 rather than a
 * filtered result, which is why the page guards on `platformAdmin` before mounting.
 */

const PAGE_SIZE = 50;
const skipOrg = { skipOrg: true as const };

function pageParams(cursor?: string | null, status?: string) {
  const params = new URLSearchParams({ limit: String(PAGE_SIZE) });
  if (cursor) params.set("cursor", cursor);
  if (status) params.set("status", status);
  return params;
}

export function useStaffRoles() {
  return useQuery({
    queryKey: ["platform-staff-me"],
    queryFn: fetchStaffMe,
    enabled: IS_ADMIN_APP,
    staleTime: 60_000,
  });
}

export function usePlatformOverview() {
  return useQuery({
    queryKey: ["platform-overview"],
    queryFn: () => apiRequest("/admin/platform/overview", platformOverviewSchema, skipOrg),
    // Operators leave this screen open; a minute-old backlog reading is misleading.
    refetchInterval: 30_000,
  });
}

export function useTenants(status?: string) {
  return useInfiniteQuery({
    queryKey: ["platform-tenants", status],
    queryFn: ({ pageParam }) =>
      apiRequest(`/admin/platform/tenants?${pageParams(pageParam, status)}`, tenantPageSchema, skipOrg),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => (last.hasMore ? last.nextCursor : undefined),
  });
}

export function useLeads(status?: string) {
  return useInfiniteQuery({
    queryKey: ["site-leads", status],
    queryFn: ({ pageParam }) =>
      apiRequest(`/admin/site/leads?${pageParams(pageParam, status)}`, leadPageSchema, skipOrg),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => (last.hasMore ? last.nextCursor : undefined),
  });
}

export function useLead(id: string | undefined) {
  return useQuery({
    queryKey: ["site-lead", id],
    queryFn: () => apiRequest(`/admin/site/leads/${id}`, leadDetailSchema, skipOrg),
    enabled: !!id,
  });
}

export function useUpdateLead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status, internalNotes }: { id: string; status: string; internalNotes?: string }) =>
      apiRequest(`/admin/site/leads/${id}`, leadDetailSchema, {
        method: "PATCH",
        body: { status, internalNotes },
        ...skipOrg,
      }),
    onSuccess: (updated) => {
      void queryClient.invalidateQueries({ queryKey: ["site-leads"] });
      queryClient.setQueryData(["site-lead", updated.id], updated);
    },
  });
}

export function useSubscribers(status?: string) {
  return useInfiniteQuery({
    queryKey: ["site-subscribers", status],
    queryFn: ({ pageParam }) =>
      apiRequest(`/admin/site/subscribers?${pageParams(pageParam, status)}`, subscriberPageSchema, skipOrg),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => (last.hasMore ? last.nextCursor : undefined),
  });
}

export function useApplications(status?: string) {
  return useInfiniteQuery({
    queryKey: ["site-applications", status],
    queryFn: ({ pageParam }) =>
      apiRequest(`/admin/site/applications?${pageParams(pageParam, status)}`, applicationPageSchema, skipOrg),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => (last.hasMore ? last.nextCursor : undefined),
  });
}

export function useApplication(id: string | undefined) {
  return useQuery({
    queryKey: ["site-application", id],
    queryFn: () => apiRequest(`/admin/site/applications/${id}`, applicationDetailSchema, skipOrg),
    enabled: !!id,
  });
}

export function useUpdateApplication() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status, internalNotes }: { id: string; status: string; internalNotes?: string }) =>
      apiRequest(`/admin/site/applications/${id}`, applicationDetailSchema, {
        method: "PATCH",
        body: { status, internalNotes },
        ...skipOrg,
      }),
    onSuccess: (updated) => {
      void queryClient.invalidateQueries({ queryKey: ["site-applications"] });
      queryClient.setQueryData(["site-application", updated.id], updated);
    },
  });
}
