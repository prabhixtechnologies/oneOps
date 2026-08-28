import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiDownload, apiRequest, apiRequestVoid, triggerBlobDownload } from "@/lib/api-client";
import { useAuth } from "@/lib/auth";
import {
  arraySchema,
  organizationViewSchema,
  userProfileSchema,
} from "@/lib/schemas/common";
import {
  auditLogPageSchema,
  apiKeyPageSchema,
  createdApiKeySchema,
  invitePageSchema,
  memberListPageSchema,
  memberSchema,
  permissionCategorySchema,
  rolePageSchema,
  roleSchema,
  sessionListSchema,
  teamMemberPageSchema,
  teamPageSchema,
  teamSchema,
} from "@/lib/schemas/org";
import {
  billingAddressSchema,
  entitlementsSchema,
  invoiceListPageSchema,
  orderSchema,
  paymentMethodListSchema,
  planPageSchema,
  subscriptionSchema,
} from "@/lib/schemas/billing";
import { z } from "zod";

function orgId(orgId: string | null): string {
  if (!orgId) throw new Error("No active organization");
  return orgId;
}

export function useMembers(search: string) {
  const { organizationId } = useAuth();
  return useInfiniteQuery({
    queryKey: ["members", organizationId, search],
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams({ limit: "50" });
      if (pageParam) params.set("cursor", pageParam);
      if (search) params.set("search", search);
      return apiRequest(
        `/organizations/${orgId(organizationId)}/members?${params}`,
        memberListPageSchema,
      );
    },
    initialPageParam: null as string | null,
    getNextPageParam: (last) => (last.hasMore ? last.nextCursor : undefined),
    enabled: !!organizationId,
  });
}

export function useRoles() {
  return useQuery({
    queryKey: ["roles"],
    queryFn: () => apiRequest("/roles", rolePageSchema),
  });
}

export function usePermissions() {
  return useQuery({
    queryKey: ["permissions"],
    queryFn: () => apiRequest("/permissions", arraySchema(permissionCategorySchema)),
  });
}

export function useTeams() {
  return useQuery({
    queryKey: ["teams"],
    queryFn: () => apiRequest("/teams", teamPageSchema),
  });
}

export function useTeamMembers(teamId: string | undefined) {
  return useQuery({
    queryKey: ["team-members", teamId],
    queryFn: () => apiRequest(`/teams/${teamId}/members`, teamMemberPageSchema),
    enabled: !!teamId,
  });
}

export function useInvites() {
  return useQuery({
    queryKey: ["invites"],
    queryFn: () => apiRequest("/invites", invitePageSchema),
  });
}

export function useCreateInvite() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { email: string; roleId: string }) =>
      apiRequest("/invites", z.record(z.string()), { method: "POST", body: data }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["invites"] }),
  });
}

export function useRevokeInvite() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiRequestVoid(`/invites/${id}/revoke`, { method: "POST" }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["invites"] }),
  });
}

export function useResendInvite() {
  return useMutation({
    mutationFn: (id: string) => apiRequestVoid(`/invites/${id}/resend`, { method: "POST" }),
  });
}

export function useUpdateRole() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, permissions }: { id: string; permissions: string[] }) =>
      apiRequest(`/roles/${id}`, roleSchema, { method: "PATCH", body: { permissions } }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["roles"] }),
  });
}

export function useChangeMemberRole() {
  const { organizationId } = useAuth();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ memberId, roleId }: { memberId: string; roleId: string }) =>
      apiRequest(
        `/organizations/${orgId(organizationId)}/members/${memberId}/role`,
        memberSchema,
        { method: "PATCH", body: { roleId } },
      ),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["members"] }),
  });
}

export function useSuspendMember() {
  const { organizationId } = useAuth();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (memberId: string) =>
      apiRequestVoid(
        `/organizations/${orgId(organizationId)}/members/${memberId}/suspend`,
        { method: "PATCH" },
      ),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["members"] }),
  });
}

export function useRemoveMember() {
  const { organizationId } = useAuth();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (memberId: string) =>
      apiRequestVoid(
        `/organizations/${orgId(organizationId)}/members/${memberId}`,
        { method: "DELETE" },
      ),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["members"] }),
  });
}

export function useCreateTeam() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { name: string; description?: string }) =>
      apiRequest("/teams", teamSchema, { method: "POST", body: data }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["teams"] }),
  });
}

export function useUpdateTeam() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, name, description }: { id: string; name: string; description?: string }) =>
      apiRequest(`/teams/${id}`, teamSchema, { method: "PATCH", body: { name, description } }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["teams"] }),
  });
}

export function useDeleteTeam() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiRequestVoid(`/teams/${id}`, { method: "DELETE" }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["teams"] }),
  });
}

export function useAddTeamMember() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ teamId, userId }: { teamId: string; userId: string }) =>
      apiRequest(`/teams/${teamId}/members`, z.object({ userId: z.string() }), {
        method: "POST",
        body: { userId },
      }),
    onSuccess: (_, { teamId }) => {
      void qc.invalidateQueries({ queryKey: ["team-members", teamId] });
      void qc.invalidateQueries({ queryKey: ["teams"] });
    },
  });
}

export function useRemoveTeamMember() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ teamId, userId }: { teamId: string; userId: string }) =>
      apiRequestVoid(`/teams/${teamId}/members/${userId}`, { method: "DELETE" }),
    onSuccess: (_, { teamId }) => {
      void qc.invalidateQueries({ queryKey: ["team-members", teamId] });
      void qc.invalidateQueries({ queryKey: ["teams"] });
    },
  });
}

export function useAuditLogs(action?: string) {
  return useInfiniteQuery({
    queryKey: ["audit-logs", action],
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams({ limit: "25" });
      if (pageParam) params.set("cursor", pageParam);
      if (action) params.set("action", action);
      return apiRequest(`/audit-logs?${params}`, auditLogPageSchema);
    },
    initialPageParam: null as string | null,
    getNextPageParam: (last) => (last.hasMore ? last.nextCursor : undefined),
  });
}

export function useSubscription() {
  return useQuery({
    queryKey: ["subscription"],
    queryFn: () => apiRequest("/billing/subscription", subscriptionSchema),
  });
}

export function useEntitlements() {
  return useQuery({
    queryKey: ["entitlements"],
    queryFn: () => apiRequest("/billing/entitlements", entitlementsSchema),
  });
}

export function usePlans() {
  return useQuery({
    queryKey: ["plans"],
    queryFn: () => apiRequest("/billing/plans", planPageSchema),
  });
}

export function useInvoices() {
  return useInfiniteQuery({
    queryKey: ["invoices"],
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams({ limit: "25" });
      if (pageParam) params.set("cursor", pageParam);
      return apiRequest(`/billing/invoices?${params}`, invoiceListPageSchema);
    },
    initialPageParam: null as string | null,
    getNextPageParam: (last) => (last.hasMore ? last.nextCursor : undefined),
  });
}

export function useDownloadInvoice() {
  return useMutation({
    mutationFn: async (id: string) => {
      const { blob, filename } = await apiDownload(`/billing/invoices/${id}/download`);
      triggerBlobDownload(blob, filename);
    },
  });
}

export function usePaymentMethods() {
  return useQuery({
    queryKey: ["payment-methods"],
    queryFn: () => apiRequest("/billing/payment-methods", paymentMethodListSchema),
  });
}

export function useBillingAddress() {
  return useQuery({
    queryKey: ["billing-address"],
    queryFn: () => apiRequest("/billing/address", billingAddressSchema),
  });
}

export function useUpdateBillingAddress() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: z.infer<typeof billingAddressSchema>) =>
      apiRequest("/billing/address", billingAddressSchema, { method: "PUT", body: data }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["billing-address"] }),
  });
}

export function useCancelSubscription() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { atPeriodEnd: boolean; reason?: string }) =>
      apiRequest("/billing/subscription/cancel", subscriptionSchema, { method: "POST", body: data }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["subscription"] }),
  });
}

export function useReactivateSubscription() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiRequest("/billing/subscription/reactivate", subscriptionSchema, { method: "POST" }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["subscription"] }),
  });
}

export function useChangeSeats() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (seats: number) =>
      apiRequest("/billing/subscription/seats", subscriptionSchema, {
        method: "POST",
        body: { seats },
      }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["subscription"] }),
  });
}

export function useCreateOrder() {
  return useMutation({
    mutationFn: (planId: string) =>
      apiRequest("/billing/orders", orderSchema, { method: "POST", body: { planId } }),
  });
}

export function useVerifyPayment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { orderId: string; paymentId: string; signature: string }) =>
      apiRequest("/billing/verify", z.object({ success: z.boolean() }), {
        method: "POST",
        body: data,
      }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["subscription"] });
      void qc.invalidateQueries({ queryKey: ["invoices"] });
    },
  });
}

export function useSessions() {
  return useQuery({
    queryKey: ["sessions"],
    queryFn: () => apiRequest("/users/me/sessions", sessionListSchema),
  });
}

export function useRevokeSession() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      apiRequestVoid(`/users/me/sessions/${id}`, { method: "DELETE" }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["sessions"] }),
  });
}

export function useApiKeys() {
  return useQuery({
    queryKey: ["api-keys"],
    queryFn: () => apiRequest("/settings/api-keys", apiKeyPageSchema),
  });
}

export function useCreateApiKey() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (name: string) =>
      apiRequest("/settings/api-keys", createdApiKeySchema, { method: "POST", body: { name } }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["api-keys"] }),
  });
}

export function useRevokeApiKey() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      apiRequestVoid(`/settings/api-keys/${id}`, { method: "DELETE" }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["api-keys"] }),
  });
}

export function useProfile() {
  return useQuery({
    queryKey: ["profile"],
    queryFn: () => apiRequest("/users/me", userProfileSchema),
  });
}

export function useUpdateProfile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: {
      fullName?: string;
      displayName?: string;
      jobTitle?: string;
      timezone?: string;
      locale?: string;
    }) => apiRequest("/users/me", userProfileSchema, { method: "PATCH", body: data }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["profile"] });
    },
  });
}

export function useChangePassword() {
  return useMutation({
    mutationFn: (data: { currentPassword: string; newPassword: string }) =>
      apiRequestVoid("/users/me/password", { method: "POST", body: data }),
  });
}

export function useUpdateNotificationPrefs() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (preferences: Record<string, unknown>) =>
      apiRequest("/users/me/notification-prefs", userProfileSchema, {
        method: "PATCH",
        body: { preferences },
      }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["profile"] }),
  });
}

export function useUploadAvatar() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (file: File) => {
      const { apiUpload } = await import("@/lib/api-client");
      return apiUpload("/users/me/avatar", file, userProfileSchema);
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["profile"] }),
  });
}

export function useOrganization(id: string | null) {
  return useQuery({
    queryKey: ["organization", id],
    queryFn: () => apiRequest(`/organizations/${id}`, organizationViewSchema),
    enabled: !!id,
  });
}

export function useUpdateOrganization() {
  const { organizationId } = useAuth();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { name?: string; timezone?: string; locale?: string }) =>
      apiRequest(`/organizations/${orgId(organizationId)}`, organizationViewSchema, {
        method: "PATCH",
        body: data,
      }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["organization"] });
    },
  });
}
