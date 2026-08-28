import { Outlet } from "react-router";

import { LogoMark } from "@/components/brand/LogoMark";

export function AuthShell() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-surface-muted p-4 mesh-bg">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <LogoMark className="mx-auto mb-4 h-12 w-12" />
          <h1 className="text-2xl font-semibold tracking-tight">Prabhix</h1>
          <p className="mt-1 text-sm text-text-muted">Team inbox & operations platform</p>
        </div>
        <div className="rounded-xl border border-border bg-surface p-6 shadow-sm">
          <Outlet />
        </div>
      </div>
    </div>
  );
}
