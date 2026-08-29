import { createContext, useContext, type ReactNode } from "react";

/**
 * Which of the two consoles this bundle is.
 *
 * <p>Passed down from the entry point rather than read from an environment variable, because both
 * apps are built from one source tree and shared modules cannot be compiled twice with different
 * constants. Anything that must differ between the two — today the sidebar, tomorrow the tenant
 * banner — asks for it here.
 */
export type AppMode = "admin" | "oneops";

const AppModeContext = createContext<AppMode>("oneops");

export function AppModeProvider({ mode, children }: { mode: AppMode; children: ReactNode }) {
  return <AppModeContext.Provider value={mode}>{children}</AppModeContext.Provider>;
}

export function useAppMode(): AppMode {
  return useContext(AppModeContext);
}
