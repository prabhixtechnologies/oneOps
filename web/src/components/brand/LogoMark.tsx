import { useId } from "react";

type LogoMarkProps = {
  className?: string;
};

/**
 * The Prabhix "PA" monogram — P for Priti, A for Abhishek.
 *
 * The path data is shared with marketing/src/components/logo-mark.tsx, web/public/favicon.svg and
 * the Android launcher icon. Previously the console rendered a bare text "P" in a div here and in
 * the sidebar while marketing drew a different mark, so the brand did not match between the site
 * and the product. Keep those copies in step when changing this.
 *
 * The rounded rect is part of the SVG rather than a Tailwind rounded-* class so the corner radius
 * stays proportional at every size this renders at.
 *
 * The tile is filled with the active product's accent pair, so the same component draws an
 * indigo-to-fuchsia mark in OneOps and a violet-to-cyan one in Admin. It previously hardcoded
 * the house cyan, which is what made the two consoles indistinguishable.
 */
export function LogoMark({ className }: LogoMarkProps) {
  // Gradient ids are document-global, so the sidebar and header marks would otherwise share one.
  const gradientId = useId();

  return (
    <svg
      viewBox="0 0 36 36"
      className={className}
      role="img"
      aria-label="Prabhix"
      xmlns="http://www.w3.org/2000/svg"
    >
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="var(--px-accent)" />
          <stop offset="1" stopColor="var(--px-accent-2)" />
        </linearGradient>
      </defs>
      <rect width="36" height="36" rx="10" fill={`url(#${gradientId})`} />
      <path
        fill="var(--px-accent-ink)"
        d="M10 24V12h4.2c3.2 0 5 1.8 5 3.7 0 1.9-1.9 3.8-5 3.8v4.5H10zm4.2-6.5h2c1.1 0 1.8-.6 1.8-1.5 0-.9-.7-1.5-1.8-1.5h-2z"
      />
      <path
        fill="var(--px-accent-2-subtle-border)"
        d="M22.5 12h3.5l5 12h-3.7l-.9-2.3h-4.5l-.9 2.3H17l5.5-12zm2.2 7.1l-1.5-3.8-1.5 3.8h3z"
      />
    </svg>
  );
}
