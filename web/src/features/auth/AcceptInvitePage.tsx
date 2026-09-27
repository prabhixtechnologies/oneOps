import { Link, useNavigate, useParams } from "react-router";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { apiRequest, getApiErrorMessage } from "@/lib/api-client";
import { Skeleton } from "@/components/ui/skeleton";
import { useEffect, useState } from "react";
import { z } from "zod";
import { useAuth } from "@/lib/auth";
import { beginAccountSwitch, beginLogin, beginSignup, isOidcEnabled } from "@/lib/oidc";

const inviteReturnPath = (token: string) => `/invite/${encodeURIComponent(token)}`;

export function AcceptInvitePage() {
  const { token } = useParams<{ token: string }>();
  const navigate = useNavigate();
  const { isAuthenticated, isLoading, profile, refreshSession } = useAuth();
  const [preview, setPreview] = useState<{ email: string; organizationName: string; roleName: string } | null>(null);
  const [loadingPreview, setLoadingPreview] = useState(true);
  const [accepting, setAccepting] = useState(false);

  useEffect(() => {
    if (!token) {
      setLoadingPreview(false);
      return;
    }
    void (async () => {
      try {
        const data = await apiRequest(
          `/oneops/auth/invites/preview?token=${encodeURIComponent(token)}`,
          z.object({ email: z.string(), organizationName: z.string(), roleName: z.string() }),
          { skipAuth: true },
        );
        setPreview(data);
      } catch {
        toast.error("Invalid or expired invite link.");
      } finally {
        setLoadingPreview(false);
      }
    })();
  }, [token]);

  const emailMatches = preview && profile?.email.toLowerCase() === preview.email.toLowerCase();
  const emailVerified = profile?.emailVerified ?? false;

  const acceptInvite = async () => {
    if (!token) return;
    setAccepting(true);
    try {
      const result = await apiRequest(
        "/oneops/invites/accept",
        z.object({ organizationId: z.string() }),
        { method: "POST", body: { token } },
      );
      await refreshSession().catch(() => false);
      toast.success(`You joined ${preview?.organizationName ?? "the organization"}.`);
      void navigate(`/organizations?id=${result.organizationId}`);
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    } finally {
      setAccepting(false);
    }
  };

  if (loadingPreview || isLoading) {
    return (
      <div className="space-y-4 text-center">
        <Skeleton className="mx-auto h-8 w-48" />
        <p className="text-sm text-text-muted">Loading invitation…</p>
      </div>
    );
  }

  if (!token || !preview) {
    return (
      <div className="space-y-4 text-center">
        <p className="text-sm text-destructive">This invite link is no longer valid.</p>
        <Button onClick={() => void navigate("/login")}>Go to sign in</Button>
      </div>
    );
  }

  if (!isAuthenticated) {
    return (
      <div className="space-y-6">
        <div>
          <h2 className="text-lg font-semibold">Accept invitation</h2>
          <p className="text-sm text-text-muted">
            Join <strong>{preview.organizationName}</strong> as {preview.roleName} ({preview.email})
          </p>
        </div>
        <p className="text-sm text-text-muted">
          Sign in or create a Prabhix Identity account with <strong>{preview.email}</strong>, then return here to accept.
        </p>
        {isOidcEnabled() ? (
          <div className="space-y-2">
            <Button className="w-full" onClick={() => void beginLogin(inviteReturnPath(token))}>
              Sign in to accept
            </Button>
            <Button className="w-full" variant="outline" onClick={() => void beginSignup(inviteReturnPath(token))}>
              Create account
            </Button>
          </div>
        ) : (
          <Button className="w-full" onClick={() => void navigate("/login")}>
            Go to sign in
          </Button>
        )}
      </div>
    );
  }

  if (!emailMatches) {
    return (
      <div className="space-y-6">
        <p className="text-sm text-destructive">
          You are signed in as {profile?.email}, but this invitation was sent to {preview.email}.
        </p>
        <Button className="w-full" onClick={() => void beginAccountSwitch(inviteReturnPath(token))}>
          Switch account
        </Button>
      </div>
    );
  }

  if (!emailVerified) {
    return (
      <div className="space-y-6">
        <p className="text-sm text-text-muted">
          Verify {preview.email} on your Identity account before joining {preview.organizationName}.
        </p>
        <Button asChild variant="outline">
          <Link to="/settings">Open settings</Link>
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold">Accept invitation</h2>
        <p className="text-sm text-text-muted">
          Join <strong>{preview.organizationName}</strong> as {preview.roleName}
        </p>
      </div>
      <Button type="button" className="w-full" disabled={accepting} onClick={() => void acceptInvite()}>
        {accepting ? "Joining…" : "Accept & join"}
      </Button>
    </div>
  );
}
