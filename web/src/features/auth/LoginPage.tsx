import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Link, useNavigate } from "react-router";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { apiRequest, getApiErrorMessage } from "@/lib/api-client";
import { ackResponseSchema, authTokensSchema } from "@/lib/schemas/common";
import { useAuth } from "@/lib/auth";
import { GOOGLE_SSO_ENABLED } from "@/lib/config";
import { passwordSchema as password } from "@/lib/password";

const emailSchema = z.object({ email: z.string().email("Enter a valid email") });
const passwordSchema = emailSchema.extend({ password });
const otpSchema = emailSchema.extend({
  code: z.string().min(4, "Enter the verification code"),
});

export function LoginPage() {
  const { login, loginWithTokens } = useAuth();
  const navigate = useNavigate();

  const passwordForm = useForm<z.infer<typeof passwordSchema>>({
    resolver: zodResolver(passwordSchema),
    defaultValues: { email: "", password: "" },
  });

  const magicForm = useForm<z.infer<typeof emailSchema>>({
    resolver: zodResolver(emailSchema),
  });

  const otpRequestForm = useForm<z.infer<typeof emailSchema>>({
    resolver: zodResolver(emailSchema),
  });

  const otpVerifyForm = useForm<z.infer<typeof otpSchema>>({
    resolver: zodResolver(otpSchema),
  });

  const handleError = (err: unknown) => {
    toast.error(getApiErrorMessage(err));
  };

  const onPasswordLogin = passwordForm.handleSubmit(async (data) => {
    try {
      await login(data.email, data.password);
      void navigate("/");
    } catch (err) {
      handleError(err);
    }
  });

  const onMagicLink = magicForm.handleSubmit(async (data) => {
    try {
      await apiRequest("/auth/magic-link/request", ackResponseSchema, {
        method: "POST",
        body: data,
        skipAuth: true,
      });
      toast.success("Magic link sent. Check your inbox.");
    } catch (err) {
      handleError(err);
    }
  });

  const onOtpRequest = otpRequestForm.handleSubmit(async (data) => {
    try {
      await apiRequest("/auth/otp/request", ackResponseSchema, {
        method: "POST",
        body: data,
        skipAuth: true,
      });
      toast.success("Verification code sent.");
    } catch (err) {
      handleError(err);
    }
  });

  const onOtpVerify = otpVerifyForm.handleSubmit(async (data) => {
    try {
      const tokens = await apiRequest("/auth/otp/verify", authTokensSchema, {
        method: "POST",
        body: { email: data.email, code: data.code },
        skipAuth: true,
      });
      await loginWithTokens(tokens.accessToken);
      void navigate("/");
    } catch (err) {
      handleError(err);
    }
  });

  const onGoogle = async () => {
    toast.error("Google sign-in requires configuration. Contact your administrator.");
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold">Sign in</h2>
        <p className="text-sm text-text-muted">Access your organization workspace</p>
      </div>

      <Tabs defaultValue="password">
        <TabsList className="grid w-full grid-cols-3">
          <TabsTrigger value="password">Password</TabsTrigger>
          <TabsTrigger value="magic">Magic link</TabsTrigger>
          <TabsTrigger value="otp">Email OTP</TabsTrigger>
        </TabsList>

        <TabsContent value="password" className="space-y-4">
          <form onSubmit={onPasswordLogin} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="email">Work email</Label>
              <Input id="email" type="email" autoComplete="email" {...passwordForm.register("email")} />
              {passwordForm.formState.errors.email && (
                <p className="text-xs text-destructive">{passwordForm.formState.errors.email.message}</p>
              )}
            </div>
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <Label htmlFor="password">Password</Label>
                <Link to="/forgot-password" className="text-xs text-primary hover:underline">
                  Forgot password?
                </Link>
              </div>
              <Input id="password" type="password" autoComplete="current-password" {...passwordForm.register("password")} />
              {passwordForm.formState.errors.password && (
                <p className="text-xs text-destructive">{passwordForm.formState.errors.password.message}</p>
              )}
            </div>
            <Button type="submit" className="w-full" disabled={passwordForm.formState.isSubmitting}>
              Sign in
            </Button>
          </form>
        </TabsContent>

        <TabsContent value="magic" className="space-y-4">
          <form onSubmit={onMagicLink} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="magic-email">Work email</Label>
              <Input id="magic-email" type="email" {...magicForm.register("email")} />
            </div>
            <Button type="submit" className="w-full" variant="secondary">
              Send magic link
            </Button>
          </form>
        </TabsContent>

        <TabsContent value="otp" className="space-y-4">
          <form onSubmit={onOtpRequest} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="otp-email">Work email</Label>
              <Input id="otp-email" type="email" {...otpRequestForm.register("email")} />
            </div>
            <Button type="submit" className="w-full" variant="secondary">
              Send code
            </Button>
          </form>
          <form onSubmit={onOtpVerify} className="space-y-4 border-t border-border pt-4">
            <div className="space-y-2">
              <Label htmlFor="otp-code">Verification code</Label>
              <Input id="otp-code" inputMode="numeric" {...otpVerifyForm.register("code")} />
            </div>
            <Button type="submit" className="w-full">
              Verify & sign in
            </Button>
          </form>
        </TabsContent>
      </Tabs>

      {GOOGLE_SSO_ENABLED && (
        <>
          <div className="relative">
            <div className="absolute inset-0 flex items-center">
              <span className="w-full border-t border-border" />
            </div>
            <div className="relative flex justify-center text-xs uppercase">
              <span className="bg-surface px-2 text-text-muted">Or</span>
            </div>
          </div>
          <Button variant="outline" className="w-full" onClick={() => void onGoogle()}>
            Continue with Google
          </Button>
        </>
      )}

      <p className="text-center text-sm text-text-muted">
        New to Prabhix?{" "}
        <Link to="/signup" className="text-primary hover:underline">
          Create an account
        </Link>
      </p>
    </div>
  );
}
