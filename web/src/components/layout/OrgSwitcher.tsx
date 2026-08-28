import { Building2, Check, ChevronsUpDown } from "lucide-react";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Skeleton } from "@/components/ui/skeleton";
import { useAuth, useOrganizations } from "@/lib/auth";

export function OrgSwitcher() {
  const { me, organization, switchOrg } = useAuth();
  const orgsQuery = useOrganizations();

  if (!me) return null;

  const orgs = orgsQuery.data ?? (organization ? [organization] : []);
  const activeName = organization?.name ?? orgs.find((o) => o.id === me.organizationId)?.name ?? "Organization";

  if (orgsQuery.isLoading) {
    return <Skeleton className="h-9 w-36" />;
  }

  if (orgs.length <= 1) {
    return (
      <div className="flex items-center gap-2 text-sm font-medium">
        <Building2 className="h-4 w-4 text-text-muted" aria-hidden="true" />
        <span className="max-w-[180px] truncate">{activeName}</span>
      </div>
    );
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" size="sm" className="gap-2" aria-label="Switch organization">
          <Building2 className="h-4 w-4" />
          <span className="max-w-[140px] truncate">{activeName}</span>
          <ChevronsUpDown className="h-3 w-3 opacity-50" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-56">
        <DropdownMenuLabel>Organizations</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {orgs.map((org) => (
          <DropdownMenuItem
            key={org.id}
            onClick={() => void switchOrg(org.id)}
            className="gap-2"
          >
            <Avatar className="h-6 w-6">
              <AvatarFallback className="text-[10px]">
                {org.name.slice(0, 2).toUpperCase()}
              </AvatarFallback>
            </Avatar>
            <div className="flex-1 truncate">
              <div className="truncate text-sm">{org.name}</div>
              <div className="text-xs text-text-muted">{org.slug}</div>
            </div>
            {org.id === me.organizationId && <Check className="h-4 w-4 text-primary" />}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
