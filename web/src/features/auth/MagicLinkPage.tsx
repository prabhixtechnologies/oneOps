import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { toast } from "sonner";
import { apiRequest, getApiErrorMessage } from "@/lib/api-client";
import { authTokensSchema } from "@/lib/schemas/common";
import { useAuth } from "@/lib/auth";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { Link } from "react-router";

export function MagicLinkPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token") ?? "";
  const { loginWithTokens } = useAuth();
  const navigate = useNavigate();
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (!token) {
      setFailed(true);
      return;
    }
    void (async () => {
      try {
        const tokens = await apiRequest("/auth/magic-link/verify", authTokensSchema, {
          method: "POST",
          body: { token },
          skipAuth: true,
        });
        await loginWithTokens(tokens.accessToken);
        toast.success("Signed in successfully.");
        void navigate("/", { replace: true });
      } catch (err) {
        setFailed(true);
        toast.error(getApiErrorMessage(err));
      }
    })();
  }, [token, loginWithTokens, navigate]);

  if (failed) {
    return (
      <div className="space-y-4 text-center">
        <p className="text-sm text-destructive">This magic link is invalid or has expired.</p>
        <Button asChild>
          <Link to="/login">Go to sign in</Link>
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
