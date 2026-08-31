import { useEffect } from "react";
import { beginLogin, isOidcEnabled } from "@/lib/oidc";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * Signing in is a redirect, and nothing else.
 *
 * <p>The password form that used to be here is gone rather than kept behind a flag. Credentials
 * belong on the hosted page, on identity's origin, because that is the origin whose session cookie
 * makes signing in here carry over to the admin console, to Mailroom, and to every future Prabhix
 * site. A console with its own form sets a cookie on its own origin, which is as many sessions as
 * there are products — single sign-on in the marketing copy and nowhere else.
 *
 * <p>It also keeps a password out of this app entirely: a cross-site scripting hole in a console that
 * never renders a password field cannot steal one. That is only true while there is no second path,
 * so the second path is deleted rather than disabled.
 */
export function LoginPage() {
  useEffect(() => {
    if (!isOidcEnabled()) return;
    void beginLogin(window.location.pathname === "/login" ? "/" : undefined);
  }, []);

  if (!isOidcEnabled()) return <MissingIssuer />;

  return (
    <div className="space-y-4 text-center">
      <Skeleton className="mx-auto h-8 w-48" />
      <p className="text-sm text-text-muted">Taking you to sign in…</p>
    </div>
  );
}

/**
 * For a build with no `VITE_IDENTITY_ISSUER`, which has no way to sign anybody in.
 *
 * <p>Says which setting is missing, because the only person who will ever see this is whoever built
 * the image, and "sign-in is unavailable" would send them looking at the running services instead of
 * at the build arguments.
 */
export function MissingIssuer() {
  return (
    <div className="space-y-2 text-center">
      <p className="text-sm font-medium">Sign-in is not configured for this build.</p>
      <p className="text-sm text-text-muted">
        Set <code className="rounded bg-surface-muted px-1">VITE_IDENTITY_ISSUER</code> to the
        identity service URL and rebuild.
      </p>
    </div>
  );
}
