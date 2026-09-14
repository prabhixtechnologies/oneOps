# Playwright E2E (core loops)

Specs in this folder exercise the four product loops against a **seeded local stack**. They skip
unless `E2E_LIVE=1`. CI always installs Playwright and typechecks; the live job runs only when
the `E2E_LIVE` repository variable is `1`.

```powershell
cd oneOps\web
npm ci
npx playwright install chromium
$env:E2E_LIVE="1"
npx playwright test
```

## Environment

| Variable | Default | What it is |
| --- | --- | --- |
| `E2E_LIVE` | unset | Must be `1` or every test skips |
| `E2E_BASE_URL` | `http://127.0.0.1:5173` | OneOps console |
| `E2E_MARKETING_URL` | `http://127.0.0.1:3000` | Storefront + chat widget |
| `E2E_IDENTITY_URL` | `http://127.0.0.1:8081` | Hosted signup / login |
| `E2E_AGENT_EMAIL` | `ci@prabhixtechnologies.com` | Seeded owner (see Identity + oneOps `deploy/seed.sql`) |
| `E2E_AGENT_PASSWORD` | `ci-seed-password` | Same seed |
| `E2E_VISITOR_NAME` / `E2E_VISITOR_EMAIL` | E2E Visitor / visitor@example.com | Chat widget + checkout |
| `E2E_SIGNUP_EMAIL` / `E2E_SIGNUP_PASSWORD` | generated / `e2e-pass-word-10` | Billing signup loop |
| `E2E_SIGNUP_NAME` / `E2E_WORKSPACE_NAME` | E2E Signup / E2E Workspace | Identity `/signup` |

Bring the stack up from Infra (`docker compose … --profile identity --profile mailroom`). Razorpay
must be in **test mode**. Webhooks for billing and commerce should point at the public API the
stack exposes (or Mailpit + a tunnel).

## Loops

1. **Chat** — marketing launcher → visitor message → `/chat` agent reply.
2. **Commerce** — `/shop` add to cart → checkout → Razorpay test card → `/commerce/orders` fulfil.
3. **Mail** — a ticket already in `/inbox` (send inbound to the seeded shared mailbox first) → reply.
4. **Billing** — Identity `/signup` (trial org) → `/billing` upgrade → test-mode Razorpay → webhook
   capture → period end shown on reload.

Selectors are `data-testid` values on the current UI (`chat-launcher`, `order-fulfill`,
`mail-reply-send`, `plan-upgrade`, Identity `signup-form`).
