import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate, useSearchParams } from "react-router";
import { toast } from "sonner";
import { PageHeader } from "@/components/shared/PageHeader";
import { RelativeTime } from "@/components/shared/RelativeTime";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Skeleton } from "@/components/ui/skeleton";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { useAuth } from "@/lib/auth";
import { getApiErrorMessage } from "@/lib/api-client";
import { PERMISSIONS } from "@/lib/permissions";
import { ACCOUNT_URL } from "@/lib/config";
import {
  useApiKeys,
  useCreateApiKey,
  useOrganization,
  useProfile,
  useRevokeApiKey,
  useUpdateNotificationPrefs,
  useUpdateOrganization,
  useUpdateProfile,
  useUploadAvatar,
} from "@/features/org/api";

export default function SettingsPage() {
  const { me, organization, organizationId } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const tab =
    location.pathname.endsWith("/api-keys")
      ? "api-keys"
      : (searchParams.get("tab") ?? "organization");
  const profileQuery = useProfile();
  const orgQuery = useOrganization(organizationId);
  const apiKeysQuery = useApiKeys();
  const updateProfile = useUpdateProfile();
  const updateNotif = useUpdateNotificationPrefs();
  const updateOrg = useUpdateOrganization();
  const uploadAvatar = useUploadAvatar();
  const createApiKey = useCreateApiKey();
  const revokeApiKey = useRevokeApiKey();
  const avatarInputRef = useRef<HTMLInputElement>(null);

  const [displayName, setDisplayName] = useState("");
  const [fullName, setFullName] = useState("");
  const [emailNotif, setEmailNotif] = useState(true);
  const [pushNotif, setPushNotif] = useState(false);
  const [orgName, setOrgName] = useState("");

  useEffect(() => {
    if (profileQuery.data) {
      setDisplayName(profileQuery.data.displayName ?? "");
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

      <Tabs
        value={tab}
        onValueChange={(value) => {
          if (value === "api-keys") {
            void navigate("/settings/api-keys");
            return;
          }
          void navigate(value === "organization" ? "/settings" : `/settings?tab=${value}`);
        }}
      >
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
            <Label htmlFor="profile-email">Email</Label>
            <Input id="profile-email" value={me.email} disabled />
          </div>
          <Button onClick={() => void saveProfile()} disabled={updateProfile.isPending}>
            Save profile
          </Button>
        </TabsContent>

        <TabsContent value="security" className="mt-4 space-y-6">
          <div className="max-w-md space-y-3">
            <h3 className="font-medium">Password and sessions</h3>
            <p className="text-sm text-text-muted">
              Password, passkeys, and signed-in devices are managed on your Prabhix Identity account.
            </p>
            {ACCOUNT_URL ? (
              <Button asChild>
                <a href={ACCOUNT_URL}>Manage account</a>
              </Button>
            ) : (
              <p className="text-sm text-text-muted">Identity is not configured in this build.</p>
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
            <Label htmlFor="org-name">Organization name</Label>
            <Input id="org-name" value={orgName} onChange={(e) => setOrgName(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="org-slug">Slug</Label>
            <Input id="org-slug" value={orgQuery.data?.slug ?? organization?.slug ?? ""} disabled />
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
