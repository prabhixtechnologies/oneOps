import { Outlet, Link } from "react-router";

import { LogoMark } from "@/components/brand/LogoMark";
import { ThemeToggle } from "@/components/ThemeToggle";

/**
 * Auth surfaces are a brand moment, not a grey form on white.
 * Atmosphere + motion live in CSS; the outlet is the only interaction card.
 */
export function AuthShell() {
  return (
    <div className="auth-stage relative flex min-h-[100dvh] overflow-hidden">
      <div className="auth-aurora" aria-hidden />
      <div className="auth-grid" aria-hidden />
      <div className="auth-orb auth-orb--a" aria-hidden />
      <div className="auth-orb auth-orb--b" aria-hidden />
      <div className="auth-orb auth-orb--c" aria-hidden />

      <div className="absolute top-4 right-4 z-20 sm:top-6 sm:right-6">
        <ThemeToggle />
      </div>

      <div className="relative z-10 mx-auto grid w-full max-w-6xl flex-1 items-center gap-10 px-5 py-16 lg:grid-cols-[1.1fr_0.9fr] lg:gap-16 lg:px-10">
        <aside className="auth-brand-panel mx-auto max-w-xl text-center lg:mx-0 lg:text-left">
          <div className="auth-brand-mark inline-flex items-center gap-3 lg:flex">
            <LogoMark className="h-14 w-14 shrink-0 drop-shadow-lg" />
            <div>
              <p className="font-display text-3xl font-semibold tracking-tight text-text sm:text-4xl">
                OneOps
              </p>
              <p className="mt-1 text-sm text-text-muted sm:text-base">
                by Prabhix Technologies
              </p>
            </div>
          </div>
          <h1 className="auth-headline mt-8 font-display text-4xl font-semibold tracking-tight text-balance sm:text-5xl lg:text-[3.25rem] lg:leading-[1.05]">
            Sign in once.
            <span className="auth-headline-accent block">Work everywhere.</span>
          </h1>
          <p className="mt-5 max-w-md text-base leading-relaxed text-text-muted lg:text-lg">
            Operator console for inbox, chat, and day-to-day operations — secured by Prabhix Identity.
          </p>
          <p className="mt-8 hidden text-sm text-text-muted lg:block">
            One Prabhix Identity across OneOps, Mailroom, MobiStack, and more.
          </p>
        </aside>

        <section className="auth-panel mx-auto w-full max-w-md justify-self-center lg:justify-self-end">
          <div className="auth-panel-inner rounded-2xl border border-border/80 bg-surface/75 p-7 shadow-px backdrop-blur-xl sm:p-8">
            <Outlet />
          </div>
          <p className="mt-5 text-center text-sm text-text-muted">
            New workspace?{" "}
            <Link className="font-medium text-primary underline-offset-4 hover:underline" to="/signup">
              Create an account
            </Link>
          </p>
        </section>
      </div>
    </div>
  );
}
