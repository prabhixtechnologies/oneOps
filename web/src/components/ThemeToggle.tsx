import { Moon, Sun } from "lucide-react";

import { useTheme } from "@/lib/theme";
import { cn } from "@/lib/utils";

export function ThemeToggle({ className }: { className?: string }) {
  const { theme, toggleTheme } = useTheme();
  const dark = theme === "dark";

  return (
    <button
      type="button"
      onClick={toggleTheme}
      className={cn(
        "inline-flex min-h-11 items-center gap-2 rounded-full border border-border/80 bg-surface/70 px-3.5 text-sm font-medium text-text-muted shadow-sm backdrop-blur-md transition hover:border-primary/40 hover:text-text",
        className,
      )}
      aria-label={dark ? "Switch to light theme" : "Switch to dark theme"}
    >
      {dark ? <Sun className="size-4" aria-hidden /> : <Moon className="size-4" aria-hidden />}
      <span className="hidden sm:inline">{dark ? "Light" : "Dark"}</span>
    </button>
  );
}
