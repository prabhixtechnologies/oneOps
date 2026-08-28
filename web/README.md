# Prabhix Web Console

Customer-facing SaaS application for the Prabhix enterprise platform — shared team inbox, mail management, billing, and organization administration.

## Quick start

```bash
cd web
cp .env.example .env
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173). The app requires the backend API at `http://localhost:8080`.

## Environment variables

| Variable | Default | Description |
|---|---|---|
| `VITE_API_URL` | `http://localhost:8080` | Backend API base URL |
| `VITE_RAZORPAY_KEY_ID` | — | Razorpay test/live key for checkout |
| `VITE_GOOGLE_SSO_ENABLED` | `false` | Show Google SSO button (requires server config) |

## Scripts

```bash
npm run dev        # Vite dev server
npm run build      # Production build
npm run preview    # Preview production build
npm run typecheck  # TypeScript strict check
```

## Stack

React 19 · Vite 7 · TypeScript strict · Tailwind CSS v4 · shadcn/ui · TanStack Query v5 · React Router v7 · Zod · react-hook-form
