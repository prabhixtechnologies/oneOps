import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { apiRequest, getApiErrorMessage } from "@/lib/api-client";
import { ackResponseSchema } from "@/lib/schemas/common";
import { useAuth } from "@/lib/auth";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * Consumes the token from a verification email.
 *
 * Registered in `unguardedRoutes` rather than `publicRoutes`: that group sends an authenticated
 * visitor to `/`, and the ordinary case here is somebody already signed in who asked for the email
 * from their settings page. Under the public guard they would be bounced before the token was ever
 * consumed, so the address would stay unverified no matter how many times they clicked the link.
 */
export function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token") ?? "";
  const { isAuthenticated, refreshSession } = useAuth();
  const [state, setState] = useState<"working" | "done" | "failed">("working");
  const [message, setMessage] = useState("");
  // The token is single-use, so a second POST always fails. React 18 mounts effects twice in
  // development, which would show every developer a spurious "link is not valid".
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;

    if (!token) {
      setState("failed");
      setMessage("That link is missing its token.");
      return;
    }

    void (async () => {
      try {
        await apiRequest("/auth/email/verify/confirm", ackResponseSchema, {
          method: "POST",
          body: { token },
          skipAuth: true,
        });
        // Picks up the now-verified address, so a signed-in user does not keep seeing the banner
        // asking them to confirm it. Failure here is not the user's problem — the address is
        // verified either way, and the next sign-in reads the new state.
        if (isAuthenticated) await refreshSession().catch(() => false);
        setState("done");
      } catch (err) {
        setState("failed");
        setMessage(getApiErrorMessage(err));
      }
    })();
  }, [token, isAuthenticated, refreshSession]);

  if (state === "working") {
    return (
      <div className="space-y-4 text-center">
        <Skeleton className="mx-auto h-8 w-48" />
        <p className="text-sm text-text-muted">Confirming your email address…</p>
      </div>
    );
  }

  if (state === "failed") {
    return (
      <div className="space-y-4 text-center">
        <p className="text-sm text-destructive">{message}</p>
        <p className="text-sm text-text-muted">
          Verification links expire, and each one can only be used once. You can request a new one
          from your profile settings.
        </p>
        {/* Home rather than the settings page: this component is shared by both apps and only one
            of them has a /settings route, so linking there would dead-end in the other. */}
        <Button asChild>
          <Link to={isAuthenticated ? "/" : "/login"}>
            {isAuthenticated ? "Continue" : "Go to sign in"}
          </Link>
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-4 text-center">
      <p className="text-sm font-medium">Your email address is confirmed.</p>
      <Button asChild>
        <Link to={isAuthenticated ? "/" : "/login"}>
          {isAuthenticated ? "Continue" : "Go to sign in"}
        </Link>
      </Button>
    </div>
  );
}
