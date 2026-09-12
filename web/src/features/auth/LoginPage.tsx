import { beginLogin, beginSignup, isOidcEnabled, identityIssuer } from "@/lib/oidc";
import { Button } from "@/components/ui/button";

/**
 * Product consoles never collect credentials. Sign-in / signup are always Prabhix Identity (OIDC).
 */
export function LoginPage() {
  if (!isOidcEnabled()) return <MissingIssuer />;

  return (
    <div className="auth-login space-y-6 text-center">
      <div>
        <h2 className="font-display text-2xl font-semibold tracking-tight">Welcome</h2>
        <p className="mt-2 text-sm text-text-muted">
          One Prabhix account for every product. Credentials are entered only on Identity.
        </p>
      </div>
      <div className="flex flex-col gap-3">
        <Button className="w-full" size="lg" onClick={() => void beginLogin("/")}>
          Sign in
        </Button>
        <Button
          className="w-full"
          size="lg"
          variant="outline"
          onClick={() => void beginSignup("/")}
        >
          Create an account
        </Button>
      </div>
      <p className="text-xs text-text-muted break-all">{identityIssuer()}</p>
    </div>
  );
}

export function MissingIssuer() {
  return (
    <div className="space-y-3 text-center">
      <h2 className="font-display text-xl font-semibold">Identity is required</h2>
      <p className="text-sm text-text-muted">
        This console no longer has a local password form. Build with{" "}
        <code className="rounded bg-surface-muted px-1.5 py-0.5 text-xs">VITE_IDENTITY_ISSUER</code>{" "}
        set to your Identity URL (local: <code className="text-xs">http://localhost:8081</code>).
      </p>
    </div>
  );
}
