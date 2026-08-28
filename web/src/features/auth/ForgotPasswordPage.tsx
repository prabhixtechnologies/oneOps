import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Link, useSearchParams } from "react-router";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { apiRequest, getApiErrorMessage } from "@/lib/api-client";
import { ackResponseSchema } from "@/lib/schemas/common";

const schema = z.object({ email: z.string().email() });

export function ForgotPasswordPage() {
  const form = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema) });

  const onSubmit = form.handleSubmit(async (data) => {
    try {
      await apiRequest("/auth/password/forgot", ackResponseSchema, {
        method: "POST",
        body: data,
        skipAuth: true,
      });
      toast.success("Reset link sent to your email.");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  });

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold">Reset password</h2>
        <p className="text-sm text-text-muted">We&apos;ll send a reset link to your email</p>
      </div>
      <form onSubmit={onSubmit} className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="email">Work email</Label>
          <Input id="email" type="email" {...form.register("email")} />
        </div>
        <Button type="submit" className="w-full">Send reset link</Button>
      </form>
      <p className="text-center text-sm">
        <Link to="/login" className="text-primary hover:underline">Back to sign in</Link>
      </p>
    </div>
  );
}

const resetSchema = z
  .object({
    password: z.string().min(10, "Password must be at least 10 characters"),
    confirm: z.string(),
  })
  .refine((d) => d.password === d.confirm, {
    message: "Passwords must match",
    path: ["confirm"],
  });

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token") ?? "";
  const form = useForm<z.infer<typeof resetSchema>>({ resolver: zodResolver(resetSchema) });

  const onSubmit = form.handleSubmit(async (data) => {
    if (!token) {
      toast.error("Missing reset token.");
      return;
    }
    try {
      await apiRequest("/auth/password/reset", ackResponseSchema, {
        method: "POST",
        body: { newPassword: data.password, token },
        skipAuth: true,
      });
      toast.success("Password updated. You can sign in now.");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  });

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold">Set new password</h2>
      </div>
      <form onSubmit={onSubmit} className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="password">New password</Label>
          <Input id="password" type="password" {...form.register("password")} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="confirm">Confirm password</Label>
          <Input id="confirm" type="password" {...form.register("confirm")} />
          {form.formState.errors.confirm && (
            <p className="text-xs text-destructive">{form.formState.errors.confirm.message}</p>
          )}
        </div>
        <Button type="submit" className="w-full">Update password</Button>
      </form>
    </div>
  );
}
