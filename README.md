# oneOps

The product: one backend, two consoles built from one tree, and the mobile apps.

| | |
| --- | --- |
| `backend/` | Spring Boot on Java 25, PostgreSQL, Flyway, Valkey. Organizations, memberships, roles and permissions, chat, files, commerce, billing — and mail, until that moves to Mailroom. |
| `web/` | React and Vite. `APP=oneops` builds the customer console, `APP=admin` builds the internal one. Two images, one source tree, and CI checks that neither bundle contains the other's code. |
| `mobile/` | Android (`android/`, two Gradle flavours) and iOS (`ios/`, two XcodeGen targets). `com.prabhix.operator` is Prabhix OneOps, `com.prabhix.admin` is Prabhix Admin. |
| `packages/oneops-api` | `@prabhix/oneops-api` TypeScript types, commerce Zod, money helpers (from `backend/apidocs.json`) |
| `deploy/seed.sql` | The Prabhix organization and its first owner. Here rather than in Infra because it is data for a schema this repository owns, and the CI job that applies the baseline to an empty database needs both. |

Sign-in is not here. It belongs to `Identity`, which mints tokens this backend can only verify.
How the stack runs is not here either — that is `Infra`, which holds Compose, Caddy and the deploy
scripts.

## Running it

The whole stack, from a checkout of `Infra` beside this one:

```bash
cd ../Infra && make up
```

Just the backend, against that stack's database:

```bash
cd backend && mvn spring-boot:run
```

Just a console, with hot reload:

```bash
cd web && npm run dev          # VITE_APP=admin for the other one
```

## Tests

```bash
cd backend && mvn -B verify                    # unit
cd backend && mvn -B verify -Pintegration      # adds Testcontainers, needs Docker
cd web && npm run test && npx tsc --noEmit
cd packages/oneops-api && npm ci && npm run generate   # TypeScript client from backend/apidocs.json
```

`packages/oneops-api` is `@prabhix/oneops-api`. CI regenerates it and fails if `src/schema.ts` or the snapshot drift. Refresh the snapshot from a running API with `SWAGGER_ENABLED=true` and `backend/scripts/export-openapi.ps1` (or `mvn -Pgenerate-openapi springdoc-openapi:generate`).

## Releases

CI pushes `backend`, `web` and `admin` to ECR on every push to `main`, tagged with the short SHA and
`latest`. Deploying them is a separate, deliberate step from `Infra`:

```powershell
cd ../Infra
.\deploy\deploy-remote.ps1 -BackendTag <sha> -WebTag <sha> -AdminTag <sha>
```

Each service carries its own tag because the six images in production come from five repositories,
so no single commit describes them all.
