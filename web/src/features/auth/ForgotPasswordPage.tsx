import { useEffect } from "react";
import { Link } from "react-router";
import { beginLogin, isOidcEnabled } from "@/lib/oidc";
import { Button } from "@/components/ui/button";

/**
 * Password reset lives on Identity now. This route only forwards people there so old bookmarks
 * and emails do not resurrect the platform's credential API.
 */
export function ForgotPasswordPage() {
  useEffect(() => {
    if (!isOidcEnabled()) return;
    // Hosted login is where "forgotten password" is offered next to email/password proof.
    void beginLogin("/");
  }, []);

  if (!isOidcEnabled()) {
    return (
      <div className="space-y-4 text-center">
        <h2 className="font-display text-lg font-semibold">Password reset moved</h2>
        <p className="text-sm text-text-muted">
          Reset passwords through Prabhix Identity. This console no longer issues reset mail.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-4 text-center">
      <h1 className="font-display text-2xl font-semibold tracking-tight sm:text-3xl">Opening Identity</h1>
      <p className="text-sm text-text-muted">
        Password reset is handled by Prabhix Identity.
      </p>
      <Button className="w-full" onClick={() => void beginLogin("/")}>
        Continue to Identity
      </Button>
      <p className="text-center text-sm">
        <Link to="/login" className="text-primary hover:underline">Back to sign in</Link>
      </p>
    </div>
  );
}

/** Legacy email links pointed here; send people to Identity to sign in with a new password. */
export function ResetPasswordPage() {
  useEffect(() => {
    if (isOidcEnabled()) void beginLogin("/");
  }, []);

  return (
    <div className="space-y-4 text-center">
      <h2 className="font-display text-lg font-semibold">Reset link expired for this app</h2>
      <p className="text-sm text-text-muted">
        Use Prabhix Identity to set a new password, then sign in again.
      </p>
      {isOidcEnabled() ? (
        <Button className="w-full" onClick={() => void beginLogin("/")}>
          Sign in with Identity
        </Button>
      ) : null}
    </div>
  );
}
