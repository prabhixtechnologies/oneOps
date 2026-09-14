import { RouterProvider, type createBrowserRouter } from "react-router";
import { QueryClientProvider } from "@tanstack/react-query";
import { Toaster, TooltipProvider } from "@prabhix/ui";
import { ErrorBoundary } from "@/components/shared/ErrorBoundary";
import { AuthProvider } from "@/lib/auth";
import { queryClient } from "@/lib/query";
import { ThemeProvider } from "@/lib/theme";

interface AppProps {
  /**
   * Which app this is comes from the build, not from here — see lib/app-mode.ts. Only the route
   * table differs per entry point.
   */
  router: ReturnType<typeof createBrowserRouter>;
}

export function App({ router }: AppProps) {
  return (
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <TooltipProvider>
            <ErrorBoundary>
              <RouterProvider router={router} />
            </ErrorBoundary>
            <Toaster richColors closeButton position="top-right" />
          </TooltipProvider>
        </AuthProvider>
      </QueryClientProvider>
    </ThemeProvider>
  );
}
