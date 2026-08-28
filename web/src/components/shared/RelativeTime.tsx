import { formatDistanceToNowStrict } from "date-fns";
import { cn } from "@/lib/utils";

interface RelativeTimeProps {
  date: string | Date;
  className?: string;
  addSuffix?: boolean;
}

export function RelativeTime({ date, className, addSuffix = true }: RelativeTimeProps) {
  const d = typeof date === "string" ? new Date(date) : date;
  return (
    <time dateTime={d.toISOString()} className={cn("text-text-muted", className)} title={d.toLocaleString()}>
      {formatDistanceToNowStrict(d, { addSuffix })}
    </time>
  );
}
