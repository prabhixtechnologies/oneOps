import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Link, useNavigate } from "react-router";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { apiRequest, getApiErrorMessage } from "@/lib/api-client";
import { authTokensSchema } from "@/lib/schemas/common";
import { useAuth } from "@/lib/auth";

const schema = z.object({
  fullName: z.string().min(2, "Enter your full name"),
  email: z.string().email("Enter a valid email"),
  password: z.string().min(10, "Password must be at least 10 characters"),
  organizationName: z.string().min(2, "Enter your organization name"),
});

export function SignupPage() {
  const { loginWithTokens } = useAuth();
  const navigate = useNavigate();
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
  });

  const onSubmit = form.handleSubmit(async (data) => {
    try {
      const tokens = await apiRequest("/auth/register", authTokensSchema, {
        method: "POST",
        body: data,
        skipAuth: true,
      });
      await loginWithTokens(tokens.accessToken, tokens.refreshToken);
      toast.success("Welcome to Prabhix!");
      void navigate("/");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  });

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold">Create your account</h2>
        <p className="text-sm text-text-muted">Start with a 14-day Growth trial</p>
      </div>
      <form onSubmit={onSubmit} className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="fullName">Full name</Label>
          <Input id="fullName" {...form.register("fullName")} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="email">Work email</Label>
          <Input id="email" type="email" {...form.register("email")} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="password">Password</Label>
          <Input id="password" type="password" {...form.register("password")} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="organizationName">Organization name</Label>
          <Input id="organizationName" placeholder="Acme Corporation" {...form.register("organizationName")} />
        </div>
        <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
          Create account
        </Button>
      </form>
      <p className="text-center text-sm text-text-muted">
        Already have an account?{" "}
        <Link to="/login" className="text-primary hover:underline">
          Sign in
        </Link>
      </p>
    </div>
  );
}
