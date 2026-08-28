import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { PageHeader } from "@/components/shared/PageHeader";
import { RelativeTime } from "@/components/shared/RelativeTime";
import { ErrorState } from "@/components/shared/states";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { Switch } from "@/components/ui/switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Skeleton } from "@/components/ui/skeleton";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { useAuth } from "@/lib/auth";
import { getApiErrorMessage } from "@/lib/api-client";
import { PERMISSIONS } from "@/lib/permissions";
import {
  useApiKeys,
  useChangePassword,
  useCreateApiKey,
  useOrganization,
  useProfile,
  useRevokeApiKey,
  useRevokeSession,
  useSessions,
  useUpdateNotificationPrefs,
  useUpdateOrganization,
  useUpdateProfile,
  useUploadAvatar,
} from "@/features/org/api";

export default function SettingsPage() {
  const { me, organization, organizationId } = useAuth();
  const profileQuery = useProfile();
  const orgQuery = useOrganization(organizationId);
  const sessionsQuery = useSessions();
  const apiKeysQuery = useApiKeys();
  const updateProfile = useUpdateProfile();
  const changePassword = useChangePassword();
  const updateNotif = useUpdateNotificationPrefs();
  const updateOrg = useUpdateOrganization();
  const uploadAvatar = useUploadAvatar();
  const createApiKey = useCreateApiKey();
  const revokeApiKey = useRevokeApiKey();
  const revokeSession = useRevokeSession();
  const avatarInputRef = useRef<HTMLInputElement>(null);

  const [displayName, setDisplayName] = useState("");
  const [fullName, setFullName] = useState("");
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [emailNotif, setEmailNotif] = useState(true);
  const [pushNotif, setPushNotif] = useState(false);
  const [orgName, setOrgName] = useState("");

  useEffect(() => {
    if (profileQuery.data) {
      setDisplayName(profileQuery.data.displayName);
      setFullName(profileQuery.data.fullName);
      const prefs = profileQuery.data.notificationPrefs ?? {};
      setEmailNotif(prefs.email !== false);
      setPushNotif(prefs.push === true);
    }
  }, [profileQuery.data]);

  useEffect(() => {
    if (orgQuery.data) setOrgName(orgQuery.data.name);
    else if (organization) setOrgName(organization.name);
  }, [orgQuery.data, organization]);

  if (!me) {
    return (
      <div className="p-6">
        <Skeleton className="h-64" />
      </div>
    );
  }

  const saveProfile = async () => {
    try {
      await updateProfile.mutateAsync({ displayName, fullName });
      toast.success("Profile updated");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  const onChangePassword = async () => {
    if (newPassword !== confirmPassword) {
      toast.error("Passwords do not match");
      return;
    }
    try {
      await changePassword.mutateAsync({ currentPassword, newPassword });
      toast.success("Password updated");
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  const saveNotif = async () => {
    try {
      await updateNotif.mutateAsync({ email: emailNotif, push: pushNotif });
      toast.success("Notification preferences saved");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  const saveOrg = async () => {
    try {
      await updateOrg.mutateAsync({ name: orgName });
      toast.success("Organization updated");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  return (
    <div className="space-y-6 p-4 md:p-6">
      <PageHeader title="Settings" description="Manage your profile, security, and organization preferences" />

      <Tabs defaultValue="profile">
        <TabsList className="flex h-auto flex-wrap">
          <TabsTrigger value="profile">Profile</TabsTrigger>
          <TabsTrigger value="security">Security</TabsTrigger>
          <TabsTrigger value="notifications">Notifications</TabsTrigger>
          <TabsTrigger value="organization">Organization</TabsTrigger>
          <TabsTrigger value="api-keys">API keys</TabsTrigger>
        </TabsList>

        <TabsContent value="profile" className="mt-4 max-w-md space-y-4">
          <div className="flex items-center gap-4">
            <Avatar className="h-16 w-16">
              {profileQuery.data?.avatarUrl ? (
                <AvatarImage src={profileQuery.data.avatarUrl} alt={displayName} />
              ) : null}
              <AvatarFallback>{displayName.slice(0, 2).toUpperCase()}</AvatarFallback>
            </Avatar>
            <div>
              <input
                ref={avatarInputRef}
                type="file"
                accept="image/*"
                className="hidden"
                onChange={(e) => {
                  const f = e.target.files?.[0];
                  if (f) {
                    void uploadAvatar
                      .mutateAsync(f)
                      .then(() => toast.success("Avatar updated"))
                      .catch((err) => toast.error(getApiErrorMessage(err)));
                  }
                }}
              />
              <Button
                variant="outline"
                size="sm"
                disabled={uploadAvatar.isPending}
                onClick={() => avatarInputRef.current?.click()}
              >
                Upload avatar
              </Button>
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="displayName">Display name</Label>
            <Input id="displayName" value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="fullName">Full name</Label>
            <Input id="fullName" value={fullName} onChange={(e) => setFullName(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label>Email</Label>
            <Input value={me.email} disabled />
          </div>
          <Button onClick={() => void saveProfile()} disabled={updateProfile.isPending}>
            Save profile
          </Button>
        </TabsContent>

        <TabsContent value="security" className="mt-4 space-y-6">
          <div className="max-w-md space-y-4">
            <h3 className="font-medium">Change password</h3>
            <div className="space-y-2">
              <Label>Current password</Label>
              <Input type="password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>New password</Label>
              <Input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>Confirm new password</Label>
              <Input type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} />
            </div>
            <Button variant="secondary" disabled={changePassword.isPending} onClick={() => void onChangePassword()}>
              Update password
            </Button>
          </div>
          <Separator />
          <div>
            <h3 className="mb-4 font-medium">Active sessions</h3>
            {sessionsQuery.isError ? (
              <ErrorState message="Failed to load sessions" onRetry={() => void sessionsQuery.refetch()} />
            ) : (
              <ul className="divide-y divide-border rounded-lg border border-border">
                {(sessionsQuery.data ?? []).map((s) => (
                  <li key={s.id} className="flex flex-col gap-2 px-4 py-3 text-sm sm:flex-row sm:items-center sm:justify-between">
                    <div>
                      <p className="font-medium">{s.deviceName}</p>
                      <p className="text-text-muted">{s.ipAddress}</p>
                      <RelativeTime date={s.lastSeenAt} className="text-xs" />
                    </div>
                    <div className="flex items-center gap-2">
                      {s.current && <Badge variant="success">Current</Badge>}
                      {!s.current && (
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={revokeSession.isPending}
                          onClick={() =>
                            void revokeSession
                              .mutateAsync(s.id)
                              .then(() => toast.success("Session revoked"))
                              .catch((e) => toast.error(getApiErrorMessage(e)))
                          }
                        >
                          Revoke
                        </Button>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </TabsContent>

        <TabsContent value="notifications" className="mt-4 max-w-md space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="font-medium">Email notifications</p>
              <p className="text-sm text-text-muted">SLA breaches, assignments, mentions</p>
            </div>
            <Switch checked={emailNotif} onCheckedChange={setEmailNotif} aria-label="Email notifications" />
          </div>
          <div className="flex items-center justify-between">
            <div>
              <p className="font-medium">Push notifications</p>
              <p className="text-sm text-text-muted">Browser push for urgent threads</p>
            </div>
            <Switch checked={pushNotif} onCheckedChange={setPushNotif} aria-label="Push notifications" />
          </div>
          <Button disabled={updateNotif.isPending} onClick={() => void saveNotif()}>
            Save preferences
          </Button>
        </TabsContent>

        <TabsContent value="organization" className="mt-4 max-w-md space-y-4">
          <div className="space-y-2">
            <Label>Organization name</Label>
            <Input value={orgName} onChange={(e) => setOrgName(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label>Slug</Label>
            <Input value={orgQuery.data?.slug ?? organization?.slug ?? ""} disabled />
          </div>
          <PermissionGate permission={PERMISSIONS.ORG_UPDATE}>
            <Button disabled={updateOrg.isPending} onClick={() => void saveOrg()}>
              Save
            </Button>
          </PermissionGate>
        </TabsContent>

        <TabsContent value="api-keys" className="mt-4 space-y-4">
          <PermissionGate permission={PERMISSIONS.ORG_API_KEY_MANAGE}>
            <Button
              disabled={createApiKey.isPending}
              onClick={() =>
                void createApiKey
                  .mutateAsync("New API key")
                  .then((r) => toast.success(`API key created: ${r.key}`))
                  .catch((e) => toast.error(getApiErrorMessage(e)))
              }
            >
              Create API key
            </Button>
          </PermissionGate>
          <ul className="divide-y divide-border rounded-lg border border-border">
            {apiKeysQuery.data?.items.map((key) => (
              <li key={key.id} className="flex flex-col gap-2 px-4 py-3 text-sm sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <p className="font-medium">{key.name}</p>
                  <p className="font-mono text-xs text-text-muted">{key.prefix}••••</p>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-xs text-text-muted">
                    {key.lastUsedAt ? (
                      <>
                        Last used <RelativeTime date={key.lastUsedAt} />
                      </>
                    ) : (
                      "Never used"
                    )}
                  </span>
                  <PermissionGate permission={PERMISSIONS.ORG_API_KEY_MANAGE}>
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={revokeApiKey.isPending}
                      onClick={() =>
                        void revokeApiKey
                          .mutateAsync(key.id)
                          .then(() => toast.success("API key revoked"))
                          .catch((e) => toast.error(getApiErrorMessage(e)))
                      }
                    >
                      Revoke
                    </Button>
                  </PermissionGate>
                </div>
              </li>
            ))}
          </ul>
        </TabsContent>
      </Tabs>
    </div>
  );
}
