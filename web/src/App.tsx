import { RouterProvider, type createBrowserRouter } from "react-router";
import { QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { ErrorBoundary } from "@/components/shared/ErrorBoundary";
import { AuthProvider } from "@/lib/auth";
import { AppModeProvider, type AppMode } from "@/lib/app-mode";
import { queryClient } from "@/lib/query";
import { ThemeProvider } from "@/lib/theme";

interface AppProps {
  mode: AppMode;
  router: ReturnType<typeof createBrowserRouter>;
}

export function App({ mode, router }: AppProps) {
  return (
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <AppModeProvider mode={mode}>
            <TooltipProvider>
              <ErrorBoundary>
                <RouterProvider router={router} />
              </ErrorBoundary>
              <Toaster richColors closeButton position="top-right" />
            </TooltipProvider>
          </AppModeProvider>
        </AuthProvider>
      </QueryClientProvider>
    </ThemeProvider>
  );
}
