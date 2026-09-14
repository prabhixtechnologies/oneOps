# Prabhix Web Console

Two apps from one source tree. OneOps is the operations console for a small online business:
website visitors and live chat, a shared helpdesk inbox, storefront and orders, team and billing.
The same source also builds the private console Prabhix runs the platform from.

| Build | Host | Port (dev) | What it is |
|---|---|---|---|
| `APP=oneops` (default) | `oneops.prabhixtechnologies.com` | 5173 | The product. One organization — the one the account belongs to. Prabhix's own team works here too. |
| `APP=admin` | `admin.prabhixtechnologies.com` | 5174 | Prabhix staff. The platform itself: who the customers are, what the pipeline is doing, what the logs say. |

## Tenant navigation (OneOps)

Overview; Conversations (Inbox, Live Chat, Visitors); Shop; Team (Members, Billing); Settings
(Org, Mail, AI, API keys, Advanced: Flags, Event logs, Audit, Files). Helpdesk is `/inbox`. Mail
administration is Settings → Mail. Personal mail is Mailroom, linked as "My mail" when
`VITE_MAILROOM_URL` is set.

## The boundary between them

Worth reading before adding a page, because it has been got wrong twice.

**Admin is a control tower, not a copy of the product.** It mounts the Ops Hub and platform-wide
event logs. That is all it mounts. There is no inbox, no chat, no shop, no files, no members and no
settings, because each of those acts on a single organization and admin's subject is the platform.

The deciding question for a new page is **whether it can be rendered without naming an
organization.** If yes it is a platform page: `routes-admin.tsx`. If no it is a tenant page:
`routes-tenant.tsx`, which only OneOps imports. Billing is the one page OneOps has and admin does
not — the company that owns the platform has no subscription to manage.

### Support is a handoff

There is no admin API for a customer's mail, chat or orders; `/admin/platform/*` deliberately
returns counts and directory rows only. So **"Open" in the tenant directory opens OneOps in a new
tab** with `?viewAs=<orgId>` (see `lib/staff-handoff.ts`). OneOps acts on it only once `/auth/me`
confirms the caller is a platform admin, resolves the customer's real name from the API rather than
trusting the URL, and shows an amber banner saying the access is recorded. The server is the actual
gate: it refuses `X-Prabhix-Org` naming another organization from any non-admin token.

The organization travels in the URL because it cannot travel any other way — the selection lives in
`sessionStorage`, which is per-origin. The session needs no transfer; one cookie covers both hosts.

### Why the layout is what it is

`APP` is read by `vite.config.ts`, which swaps the entry script and defines `__APP_MODE__`. That is a
build-time constant, so `IS_ADMIN_APP` folds away and each bundle keeps only its own branches.

But constant folding is not enough on its own, and this is the trap the second attempt fell into. A
`lazy(() => import(...))` at module scope is emitted into **every** bundle that imports the file
containing it, whether or not a route reaches it: Rollup cannot prove a dynamic import is free of
side effects. The tenant pages therefore live in `routes-tenant.tsx`, imported only by `routes.tsx`,
while `routes-shell.tsx` holds just the guards and sign-in that both apps need. Adding a page to the
shell adds it to both apps — which is the test for whether it belongs there.

Result, at the time of writing: OneOps is 949 KB over 74 chunks, admin 686 KB over 21.

## Quick start

```bash
cd web
cp .env.example .env
npm install
npm run dev          # OneOps on :5173
npm run dev:admin    # admin console on :5174
```

Open [http://localhost:5173](http://localhost:5173). The app requires the backend API at `http://localhost:8080`.

Both dev servers can run at once, which is the only way to check locally that one sign-in covers
both hosts.

## Environment variables

| Variable | Default | Description |
|---|---|---|
| `VITE_API_URL` | `http://localhost:8080` | Backend API base URL |
| `VITE_RAZORPAY_KEY_ID` | — | Razorpay test/live key for checkout. OneOps only; admin has no checkout |
| `VITE_GOOGLE_SSO_ENABLED` | `false` | Show Google SSO button (requires server config) |
| `VITE_ONEOPS_URL` | production host | Admin only: where "Open" sends staff. Unset in a local admin build means the button goes to production |
| `VITE_IDENTITY_ISSUER` | — | Set, the app stops rendering a password form and redirects to Identity's hosted login instead |
| `VITE_MAILROOM_URL` | — | Adds a "My mail" link to the sidebar, pointing at Prabhix Mailroom. Unset hides it |

## Scripts

```bash
npm run dev          # Vite dev server, OneOps
npm run dev:admin    # Vite dev server, admin console
npm run build        # Production build, OneOps
npm run build:admin  # Production build, admin console
npm run preview      # Preview production build
npm run typecheck    # TypeScript strict check
npm run test         # Vitest
npm run e2e:typecheck
npm run e2e          # Playwright loops; skipped unless E2E_LIVE=1 — see e2e/README.md
```

## Stack

React 19 · Vite 8 · TypeScript strict · Tailwind CSS v4 · shadcn/ui · TanStack Query v5 · React Router v7 · Zod · react-hook-form
