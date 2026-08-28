import { useAuth } from "@/lib/auth";
import { hasPermission, type Permission } from "@/lib/permissions";
import { type ReactNode } from "react";

interface PermissionGateProps {
  permission: Permission | Permission[];
  fallback?: ReactNode;
  children: ReactNode;
}

export function PermissionGate({ permission, fallback = null, children }: PermissionGateProps) {
  const { permissions } = useAuth();
  if (!hasPermission(permissions, permission)) return <>{fallback}</>;
  return <>{children}</>;
}
