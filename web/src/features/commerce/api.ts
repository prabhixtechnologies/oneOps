import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest, apiUpload } from "@/lib/api-client";
import {
  categoryListSchema,
  customerDetailSchema,
  customerListPageSchema,
  dashboardViewSchema,
  discountListSchema,
  discountViewSchema,
  downloadListSchema,
  orderDetailSchema,
  orderListPageSchema,
  productDetailSchema,
  productListPageSchema,
  refundViewSchema,
  settingsViewSchema,
  variantViewSchema,
} from "@/lib/schemas/commerce";
import { fileUploadSchema, type FilePurpose } from "@/lib/schemas/files";
import { z } from "zod";

export function useCommerceDashboard() {
  return useQuery({
    queryKey: ["commerce-dashboard"],
    queryFn: () => apiRequest("/oneops/commerce/dashboard", dashboardViewSchema),
  });
}

export function useCommerceSettings() {
  return useQuery({
    queryKey: ["commerce-settings"],
    queryFn: () => apiRequest("/oneops/commerce/settings", settingsViewSchema),
  });
}

export function useUpdateCommerceSettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiRequest("/oneops/commerce/settings", settingsViewSchema, {
        method: "PUT",
        body,
      }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["commerce-settings"] }),
  });
}

export function useCommerceProducts(filters: {
  status?: string;
  type?: string;
  featured?: boolean;
}) {
  return useInfiniteQuery({
    queryKey: ["commerce-products", filters],
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams({ limit: "50" });
      if (pageParam) params.set("cursor", pageParam);
      if (filters.status) params.set("status", filters.status);
      if (filters.type) params.set("type", filters.type);
      if (filters.featured != null) params.set("featured", String(filters.featured));
      return apiRequest(`/oneops/commerce/products?${params}`, productListPageSchema);
    },
    initialPageParam: null as string | null,
    getNextPageParam: (last) => (last.hasMore ? last.nextCursor ?? undefined : undefined),
  });
}

export function useCommerceProduct(id: string | undefined) {
  return useQuery({
    queryKey: ["commerce-product", id],
    queryFn: () => apiRequest(`/oneops/commerce/products?id=${id}`, productDetailSchema),
    enabled: !!id,
  });
}

export function useCreateCommerceProduct() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiRequest("/oneops/commerce/products", productDetailSchema, { method: "POST", body }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["commerce-products"] }),
  });
}

export function useUpdateCommerceProduct(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiRequest(`/oneops/commerce/products?id=${id}`, productDetailSchema, { method: "PUT", body }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["commerce-products"] });
      void qc.invalidateQueries({ queryKey: ["commerce-product", id] });
    },
  });
}

export function useCreateVariant(productId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiRequest(`/oneops/commerce/products/variants?id=${productId}`, variantViewSchema, {
        method: "POST",
        body,
      }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["commerce-product", productId] }),
  });
}

export function useCommerceCategories() {
  return useQuery({
    queryKey: ["commerce-categories"],
    queryFn: () =>
      apiRequest("/oneops/commerce/products/categories", categoryListSchema),
  });
}

export function useCommerceOrders(filters: {
  status?: string;
  search?: string;
  from?: string;
  to?: string;
}) {
  return useInfiniteQuery({
    queryKey: ["commerce-orders", filters],
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams({ limit: "50" });
      if (pageParam) params.set("cursor", pageParam);
      if (filters.status) params.set("status", filters.status);
      if (filters.search) params.set("search", filters.search);
      if (filters.from) params.set("from", filters.from);
      if (filters.to) params.set("to", filters.to);
      return apiRequest(`/oneops/commerce/orders?${params}`, orderListPageSchema);
    },
    initialPageParam: null as string | null,
    getNextPageParam: (last) => (last.hasMore ? last.nextCursor ?? undefined : undefined),
  });
}

export function useCommerceOrder(id: string | undefined) {
  return useQuery({
    queryKey: ["commerce-order", id],
    queryFn: () => apiRequest(`/oneops/commerce/orders?id=${id}`, orderDetailSchema),
    enabled: !!id,
  });
}

export function useFulfillOrder(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiRequest(`/oneops/commerce/orders/fulfill?id=${id}`, orderDetailSchema, { method: "POST" }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["commerce-order", id] }),
  });
}

export function useCancelOrder(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiRequest(`/oneops/commerce/orders/cancel?id=${id}`, orderDetailSchema, { method: "POST" }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["commerce-order", id] }),
  });
}

export function useAnnotateOrder(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (internalNote: string) =>
      apiRequest(`/oneops/commerce/orders?id=${id}`, orderDetailSchema, {
        method: "PATCH",
        body: { internalNote },
      }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["commerce-order", id] }),
  });
}

export function useRefundOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { orderId: string; amountPaise?: number }) =>
      apiRequest("/oneops/commerce/orders/refunds", refundViewSchema, { method: "POST", body }),
    onSuccess: (_data, vars) =>
      void qc.invalidateQueries({ queryKey: ["commerce-order", vars.orderId] }),
  });
}

export function useOrderDownloads(orderId: string | undefined) {
  return useQuery({
    queryKey: ["commerce-order-downloads", orderId],
    queryFn: () =>
      apiRequest(`/oneops/commerce/orders/downloads?id=${orderId}`, downloadListSchema),
    enabled: !!orderId,
  });
}

export function useReissueDownload(orderId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (orderItemId: string) =>
      apiRequest(
        `/oneops/commerce/orders/downloads/reissue?orderId=${orderId}&orderItemId=${orderItemId}`,
        z.object({ downloadUrl: z.string(), expiresAt: z.string() }),
        { method: "POST" },
      ),
    onSuccess: () =>
      void qc.invalidateQueries({ queryKey: ["commerce-order-downloads", orderId] }),
  });
}

export function useCommerceCustomers() {
  return useInfiniteQuery({
    queryKey: ["commerce-customers"],
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams({ limit: "50" });
      if (pageParam) params.set("cursor", pageParam);
      return apiRequest(`/oneops/commerce/customers?${params}`, customerListPageSchema);
    },
    initialPageParam: null as string | null,
    getNextPageParam: (last) => (last.hasMore ? last.nextCursor ?? undefined : undefined),
  });
}

export function useCommerceCustomer(id: string | undefined) {
  return useQuery({
    queryKey: ["commerce-customer", id],
    queryFn: () => apiRequest(`/oneops/commerce/customers?id=${id}`, customerDetailSchema),
    enabled: !!id,
  });
}

export function useCommerceDiscounts() {
  return useQuery({
    queryKey: ["commerce-discounts"],
    queryFn: () => apiRequest("/oneops/commerce/discounts", discountListSchema),
  });
}

export function useCreateDiscount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiRequest("/oneops/commerce/discounts", discountViewSchema, { method: "POST", body }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["commerce-discounts"] }),
  });
}

export function useUpdateDiscount(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiRequest(`/oneops/commerce/discounts?id=${id}`, discountViewSchema, {
        method: "PATCH",
        body,
      }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["commerce-discounts"] }),
  });
}

export function useUploadCommerceFile() {
  return useMutation({
    mutationFn: ({ file, purpose }: { file: File; purpose?: FilePurpose }) =>
      apiUpload("/files", file, fileUploadSchema, { purpose: purpose ?? "DOCUMENT" }),
  });
}
