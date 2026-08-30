import { useQuery } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api-client";
import { dashboardSchema } from "@/lib/schemas/billing";

export function useDashboard() {
  return useQuery({
    queryKey: ["dashboard"],
    queryFn: () => apiRequest("/dashboard", dashboardSchema),
  });
}
