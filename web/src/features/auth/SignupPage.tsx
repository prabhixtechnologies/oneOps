import { useEffect } from "react";
import { beginSignup, isOidcEnabled } from "@/lib/oidc";
import { MissingIssuer } from "./LoginPage";

/**
 * Signing up is a redirect too, for the same reason signing in is.
 *
 * <p>The form that used to be here posted a password to the API and then had to sign the person in
 * afterwards — which could only ever produce a session on this origin, so somebody who signed up here
 * and then opened the admin console would be asked for the password they had just chosen. Creating
 * the account on identity's origin ends with the session already in the right place.
 *
 * <p>It also settles which service owns the meaning of signing up. Both answered `/auth/register` and
 * they disagreed: the platform's created a workspace as well as an account, identity's created an
 * account alone. With no form here, there is nothing left to send to whichever service the gateway
 * happens to be pointing at.
 */
export function SignupPage() {
  useEffect(() => {
    if (!isOidcEnabled()) return;
    void beginSignup("/");
  }, []);

  if (!isOidcEnabled()) return <MissingIssuer />;

  return (
    <div className="space-y-3 text-center">
      <h1 className="font-display text-2xl font-semibold tracking-tight sm:text-3xl">Taking you to sign up</h1>
      <p className="text-sm text-text-muted">One Prabhix account for every product.</p>
    </div>
  );
}
