import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Link, useNavigate, useParams } from "react-router";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { apiRequest, getApiErrorMessage } from "@/lib/api-client";
import { Skeleton } from "@/components/ui/skeleton";
import { useEffect, useState } from "react";
import { passwordSchema } from "@/lib/password";

const acceptSchema = z.object({
  fullName: z.string().min(2, "Enter your full name"),
  password: passwordSchema,
});

export function AcceptInvitePage() {
  const { token } = useParams<{ token: string }>();
  const navigate = useNavigate();
  const [previewEmail, setPreviewEmail] = useState<string | null>(null);
  const [loadingPreview, setLoadingPreview] = useState(true);

  const form = useForm<z.infer<typeof acceptSchema>>({
    resolver: zodResolver(acceptSchema),
  });

  useEffect(() => {
    if (!token) {
      setLoadingPreview(false);
      return;
    }
    void (async () => {
      try {
        const preview = await apiRequest(
          `/oneops/auth/invites/preview?token=${encodeURIComponent(token)}`,
          z.object({ email: z.string(), organizationName: z.string(), roleName: z.string() }),
          { skipAuth: true },
        );
        setPreviewEmail(preview.email);
      } catch {
        toast.error("Invalid or expired invite link.");
      } finally {
        setLoadingPreview(false);
      }
    })();
  }, [token]);

  const onSubmit = form.handleSubmit(async (data) => {
    if (!token) return;
    try {
      await apiRequest("/oneops/invites/accept", z.object({ organizationId: z.string() }), {
        method: "POST",
        body: { token, fullName: data.fullName, password: data.password },
        skipAuth: true,
      });
      toast.success("Invitation accepted. Sign in with your new password.");
      void navigate("/login");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  });

  if (loadingPreview) {
    return (
      <div className="space-y-4 text-center">
        <Skeleton className="mx-auto h-8 w-48" />
        <p className="text-sm text-text-muted">Loading invitation…</p>
      </div>
    );
  }

  if (!token || !previewEmail) {
    return (
      <div className="space-y-4 text-center">
        <p className="text-sm text-destructive">This invite link is no longer valid.</p>
        <Button onClick={() => void navigate("/login")}>Go to sign in</Button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold">Accept invitation</h2>
        <p className="text-sm text-text-muted">Join as {previewEmail}</p>
      </div>
      <form onSubmit={onSubmit} className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="fullName">Full name</Label>
          <Input id="fullName" {...form.register("fullName")} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="password">Choose a password</Label>
          <Input id="password" type="password" {...form.register("password")} />
        </div>
        <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
          Accept & join
        </Button>
      </form>
      <p className="text-center text-sm">
        <Link to="/login" className="text-primary hover:underline">Already have an account?</Link>
      </p>
    </div>
  );
}
