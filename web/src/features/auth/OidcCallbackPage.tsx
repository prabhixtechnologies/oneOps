import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { completeLogin, rememberIdToken } from "@/lib/oidc";
import { safeAppPath } from "@/lib/safePath";
import { useAuth } from "@/lib/auth";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * Where Prabhix Identity sends the browser back after a successful sign-in.
 *
 * <p>Registered as an unguarded route. Under the public guard an already-authenticated visitor is
 * bounced to `/` before the code is redeemed, which breaks the ordinary case of signing in again on a
 * machine that still has a session; under the protected guard nobody could reach it at all, since the
 * whole purpose is to arrive here without a token yet.
 */
export function OidcCallbackPage() {
  const [searchParams] = useSearchParams();
  const { loginWithTokens } = useAuth();
  const navigate = useNavigate();
  const [message, setMessage] = useState<string | null>(null);
  // The authorization code is single-use, so a second exchange always fails. React mounts effects
  // twice in development, which would show every developer a spurious failure on every sign-in.
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;

    void (async () => {
      try {
        const { tokens, returnTo } = await completeLogin(searchParams);
        // Before the await, so a slow /auth/me cannot leave a signed-in tab with no way to sign out.
        rememberIdToken(tokens.idToken);
        await loginWithTokens(tokens.accessToken);
        // replace, so Back does not return to a URL containing a spent authorization code.
        navigate(safeAppPath(returnTo), { replace: true });
      } catch (err) {
        setMessage(err instanceof Error ? err.message : "Sign-in did not complete.");
      }
    })();
  }, [searchParams, loginWithTokens, navigate]);

  if (message) {
    return (
      <div className="space-y-4 text-center">
        <p className="text-sm text-destructive">{message}</p>
        <Button asChild>
          <Link to="/login">Back to sign in</Link>
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-4 text-center">
      <Skeleton className="mx-auto h-8 w-48" />
      <p className="text-sm text-text-muted">Signing you in…</p>
    </div>
  );
}
