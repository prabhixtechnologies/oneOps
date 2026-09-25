import { useInfiniteQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiRequest, apiRequestVoid, apiUpload } from "@/lib/api-client";
import { fileListPageSchema, fileUploadSchema, type FilePurpose } from "@/lib/schemas/files";

export function useFiles() {
  return useInfiniteQuery({
    queryKey: ["files"],
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams({ limit: "40" });
      if (pageParam) params.set("cursor", pageParam);
      return apiRequest(`/oneops/files?${params}`, fileListPageSchema);
    },
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  });
}

export function useUploadFile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ file, purpose }: { file: File; purpose?: FilePurpose }) =>
      apiUpload("/files", file, fileUploadSchema, { purpose }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["files"] }),
  });
}

export function useDeleteFile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiRequestVoid(`/oneops/files?id=${id}`, { method: "DELETE" }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["files"] }),
  });
}
