import { Menu } from "lucide-react";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/lib/auth";
import { OrgSwitcher } from "./OrgSwitcher";

interface TopbarProps {
  onOpenNav?: () => void;
}

export function Topbar({ onOpenNav }: TopbarProps) {
  const { me, profile } = useAuth();
  const displayName = profile?.displayName ?? me?.displayName ?? "";
  const initials = displayName.slice(0, 2).toUpperCase() || "?";

  return (
    <header className="flex h-14 shrink-0 items-center justify-between border-b border-border bg-surface px-4 pt-[env(safe-area-inset-top)]">
      <div className="flex items-center gap-2">
        <Button
          variant="ghost"
          size="icon"
          className="lg:hidden"
          onClick={onOpenNav}
          aria-label="Open navigation menu"
        >
          <Menu className="h-5 w-5" />
        </Button>
        <OrgSwitcher />
      </div>
      {me && (
        <div className="flex items-center gap-2">
          <Avatar className="h-8 w-8">
            {profile?.avatarUrl ? (
              <AvatarImage src={profile.avatarUrl} alt={displayName} />
            ) : null}
            <AvatarFallback>{initials}</AvatarFallback>
          </Avatar>
          <div className="hidden text-sm sm:block">
            <div className="font-medium">{displayName}</div>
            <div className="text-xs text-text-muted">{me.email}</div>
          </div>
        </div>
      )}
    </header>
  );
}
