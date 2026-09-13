import { useEffect } from "react";
import { useLocation } from "react-router";
import { beginLogin, isOidcEnabled } from "@/lib/oidc";
import { safeAppPath } from "@/lib/safePath";

/**
 * Product consoles never collect credentials. {@code /login} only forwards to Prabhix Identity.
 *
 * <p>No intermediate "Sign in" button: a second login UI on this origin is exactly the thing
 * centralising identity is meant to remove. Mailroom already redirects the same way.
 */
export function LoginPage() {
  const location = useLocation();
  const from = safeAppPath((location.state as { from?: string } | null)?.from);

  useEffect(() => {
    if (!isOidcEnabled()) return;
    void beginLogin(from);
  }, [from]);

  if (!isOidcEnabled()) return <MissingIssuer />;

  return (
    <div className="auth-login space-y-4 text-center">
      <h1 className="font-display text-2xl font-semibold tracking-tight sm:text-3xl">
        Taking you to sign in
      </h1>
      <p className="text-sm text-text-muted">One Prabhix account for every product.</p>
    </div>
  );
}

export function MissingIssuer() {
  return (
    <div className="space-y-3 text-center">
      <h1 className="font-display text-xl font-semibold">Identity is required</h1>
      <p className="text-sm text-text-muted">
        This console no longer has a local password form. Build with{" "}
        <code className="rounded bg-surface-muted px-1.5 py-0.5 text-xs">VITE_IDENTITY_ISSUER</code>{" "}
        set to your Identity URL (local: <code className="text-xs">http://localhost:8081</code>).
      </p>
    </div>
  );
}
