import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

interface ResponsiveTableProps {
  children: ReactNode;
  mobile: ReactNode;
  className?: string;
}

export function ResponsiveTable({ children, mobile, className }: ResponsiveTableProps) {
  return (
    <>
      <div className={cn("hidden md:block", className)}>{children}</div>
      <div className={cn("space-y-3 md:hidden", className)}>{mobile}</div>
    </>
  );
}

interface MobileCardProps {
  children: ReactNode;
  className?: string;
  onClick?: () => void;
}

export function MobileCard({ children, className, onClick }: MobileCardProps) {
  const Tag = onClick ? "button" : "div";
  return (
    <Tag
      type={onClick ? "button" : undefined}
      onClick={onClick}
      className={cn(
        "w-full rounded-lg border border-border bg-surface p-4 text-left text-sm shadow-sm",
        onClick && "transition-colors hover:bg-surface-muted/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
        className,
      )}
    >
      {children}
    </Tag>
  );
}

interface MobileCardRowProps {
  label: string;
  value: ReactNode;
}

export function MobileCardRow({ label, value }: MobileCardRowProps) {
  return (
    <div className="flex items-start justify-between gap-3 py-1">
      <span className="shrink-0 text-xs font-medium uppercase tracking-wide text-text-muted">{label}</span>
      <span className="min-w-0 break-words text-right [overflow-wrap:anywhere]">{value}</span>
    </div>
  );
}
