import { Link, useLocation } from "react-router";
import { AlertTriangle, ArrowLeft, Home, Search, ShieldOff } from "lucide-react";

type Kind = "not-found" | "forbidden";

const COPY: Record<Kind, { code: string; title: string; body: string; Icon: typeof AlertTriangle }> = {
  "not-found": {
    code: "404",
    title: "That page is not here",
    body: "The link may be from an older build, or the record may have been removed. Nothing is broken on your account.",
    Icon: Search,
  },
  forbidden: {
    code: "403",
    title: "You do not have access to that",
    body: "Your role does not include this area. If you need it, an organization admin can grant the permission.",
    Icon: ShieldOff,
  },
};

/**
 * A real dead-end page.
 *
 * <p>Every console used to answer an unknown URL with `<Navigate to="/" replace />`. That looks
 * tidy and hides the problem: the user cannot tell whether they mistyped, followed a stale link,
 * or lack permission, and because the redirect replaces the history entry the back button cannot
 * return them to whatever sent them here. A broken link that silently lands on the dashboard also
 * never gets reported, so it stays broken.
 *
 * <p>The path is shown deliberately — it is the one piece of information that makes a bug report
 * actionable.
 */
export function RouteError({ kind = "not-found" }: { kind?: Kind }) {
  const { pathname } = useLocation();
  const { code, title, body, Icon } = COPY[kind];

  return (
    <main
      className="mx-auto flex min-h-[70vh] w-full max-w-xl flex-col items-center justify-center px-6 text-center"
      aria-labelledby="route-error-title"
    >
      <div className="px-gradient-brand-soft grid size-20 place-items-center rounded-2xl">
        <Icon className="size-8 text-[var(--px-accent-subtle-ink)]" aria-hidden />
      </div>

      <p className="mt-6 text-sm font-semibold tracking-[0.2em] text-[var(--px-ink-faint)]">{code}</p>
      <h1 id="route-error-title" className="mt-1 text-2xl font-bold text-[var(--px-ink)]">
        {title}
      </h1>
      <p className="mt-3 text-[var(--px-ink-muted)]">{body}</p>

      <code className="mt-5 max-w-full truncate rounded-lg border border-[var(--px-border)] bg-[var(--px-surface-sunken)] px-3 py-1.5 text-xs text-[var(--px-ink-muted)]">
        {pathname}
      </code>

      <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
        <Link
          to="/"
          className="inline-flex items-center gap-2 rounded-xl bg-[var(--px-accent)] px-4 py-2.5 text-sm font-semibold text-[var(--px-accent-ink)] transition-colors hover:bg-[var(--px-accent-hover)]"
        >
          <Home className="size-4" aria-hidden />
          Go to dashboard
        </Link>
        <button
          type="button"
          onClick={() => window.history.back()}
          className="inline-flex items-center gap-2 rounded-xl border border-[var(--px-border-strong)] px-4 py-2.5 text-sm font-semibold text-[var(--px-ink)] transition-colors hover:bg-[var(--px-surface-hover)]"
        >
          <ArrowLeft className="size-4" aria-hidden />
          Back
        </button>
      </div>
    </main>
  );
}

export default RouteError;
