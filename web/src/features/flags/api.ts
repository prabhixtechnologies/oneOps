import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api-client";
import { effectiveFlagsSchema, flagDetailSchema } from "@/lib/schemas/flags";

export function useFeatureFlags() {
  return useQuery({
    queryKey: ["flags"],
    queryFn: () => apiRequest("/flags", effectiveFlagsSchema),
  });
}

export function useSetFeatureFlag() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ key, enabled }: { key: string; enabled: boolean }) =>
      apiRequest(`/flags/${key}`, flagDetailSchema, { method: "PUT", body: { enabled } }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["flags"] }),
  });
}
