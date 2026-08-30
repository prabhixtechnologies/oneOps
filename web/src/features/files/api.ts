import { useMutation } from "@tanstack/react-query";
import { apiRequestVoid, apiUpload } from "@/lib/api-client";
import { fileUploadSchema, type FilePurpose } from "@/lib/schemas/files";

export function useUploadFile() {
  return useMutation({
    mutationFn: ({ file, purpose }: { file: File; purpose?: FilePurpose }) =>
      apiUpload("/files", file, fileUploadSchema, { purpose }),
  });
}

export function useDeleteFile() {
  return useMutation({
    mutationFn: (id: string) => apiRequestVoid(`/files/${id}`, { method: "DELETE" }),
  });
}
