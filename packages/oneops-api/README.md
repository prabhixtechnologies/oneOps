# @prabhix/oneops-api

TypeScript client types for the oneOps / Prabhix Platform API.

Generated with [openapi-typescript](https://github.com/openapi-ts/openapi-typescript) from
`../../backend/apidocs.json`. Commerce Zod schemas and INR money helpers live here so the console and
the marketing storefront share one copy.

## Generate

From this directory, with Node 22:

```bash
npm ci
npm run generate
```

`pregenerate` merges `overlay.openapi.json` into the snapshot when commerce operations are missing
(the checked-in dump predates the storefront). Existing snapshot keys win, so a live springdoc
export is not clobbered.

Refresh the snapshot from a running API (`SWAGGER_ENABLED=true`):

```powershell
../../backend/scripts/export-openapi.ps1
```

or `mvn -Pgenerate-openapi -f ../../backend/pom.xml springdoc-openapi:generate` while the app is up.

CI fails if `src/schema.ts` or `backend/apidocs.json` drift from `npm run generate`.
