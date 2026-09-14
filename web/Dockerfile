# syntax=docker/dockerfile:1
# Base images come from AWS's public gallery rather than Docker Hub. These are the same
# Docker Official Images, mirrored by AWS, and the move takes Docker Hub's anonymous pull
# limit off the build path: a rate-limited base image fails the build for a reason that has
# nothing to do with the change being built.

# Build context is the oneOps repository root so the console can file:-depend on
# packages/oneops-api. CI and Infra compose pass context: <oneOps> and this Dockerfile.

# Build stage
FROM public.ecr.aws/docker/library/node:22-alpine AS build
WORKDIR /app
# Vendor web-kit under /app so Vite resolves UI peers from /app/node_modules.
# Keep oneops-api at /packages/oneops-api — web/package.json and source imports use file:../packages/oneops-api.
COPY --from=webkit packages ./vendor/web-kit/packages
COPY packages/oneops-api /packages/oneops-api
COPY web/package.json ./
# Host lockfiles are npm 8 / lockfileVersion 2. node:22-alpine ships npm 10, which
# rejects those on `npm ci` and crashes on `npm install` if the old lock is present.
RUN node -e "const fs=require('fs');const p=JSON.parse(fs.readFileSync('package.json','utf8'));const m={'@prabhix/brand':'file:./vendor/web-kit/packages/brand','@prabhix/ui':'file:./vendor/web-kit/packages/ui','@prabhix/oidc-client':'file:./vendor/web-kit/packages/oidc-client'};for (const [k,v] of Object.entries(m)){if(p.dependencies?.[k]?.startsWith('file:'))p.dependencies[k]=v;}fs.writeFileSync('package.json',JSON.stringify(p,null,2));"
RUN npm install --no-audit --no-fund
COPY web/ ./
RUN node -e "const fs=require('fs');const p=JSON.parse(fs.readFileSync('package.json','utf8'));const m={'@prabhix/brand':'file:./vendor/web-kit/packages/brand','@prabhix/ui':'file:./vendor/web-kit/packages/ui','@prabhix/oidc-client':'file:./vendor/web-kit/packages/oidc-client'};for (const [k,v] of Object.entries(m)){if(p.dependencies?.[k]?.startsWith('file:'))p.dependencies[k]=v;}fs.writeFileSync('package.json',JSON.stringify(p,null,2));"
# Selects which of the two consoles to build: "oneops" (the product) or "admin" (private). One
# source tree, two images — see the entry plugin in vite.config.ts.
ARG APP=oneops
ENV APP=$APP
# Vite inlines import.meta.env.VITE_* at build time, so every var the app reads has to be declared
# here — an arg passed by Compose but not declared in this stage is silently discarded.
ARG VITE_API_URL=http://localhost:8080
ARG VITE_RAZORPAY_KEY_ID=
ARG VITE_GOOGLE_SSO_ENABLED=false
# Admin build only: where "View as" sends staff. Unused by the OneOps build, which is the place
# being sent to.
ARG VITE_ONEOPS_URL=
# Blank until identity is the auth upstream. Set, this app stops rendering a password form at all and
# redirects to the hosted login page instead — so setting it before identity is actually serving
# /oauth2/authorize makes signing in impossible rather than merely different.
# Local laptop default: Identity on :8081. Production compose overrides to https://api.…
ARG VITE_IDENTITY_ISSUER=http://localhost:8081
# Prabhix Mailroom. Unset hides the "My mail" link, which is the right default for a build that has
# no idea whether a Mailroom is running.
ARG VITE_MAILROOM_URL=
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_RAZORPAY_KEY_ID=$VITE_RAZORPAY_KEY_ID
ENV VITE_GOOGLE_SSO_ENABLED=$VITE_GOOGLE_SSO_ENABLED
ENV VITE_ONEOPS_URL=$VITE_ONEOPS_URL
ENV VITE_IDENTITY_ISSUER=$VITE_IDENTITY_ISSUER
ENV VITE_MAILROOM_URL=$VITE_MAILROOM_URL
RUN npm run build
# The CSP names a hash for each inline script, and the hash has to come from the minified artifact
# rather than the source, so it is computed here and not checked in.
RUN node scripts/generate-security-headers.mjs dist security-headers.conf.template security-headers.generated.conf

# Production stage
FROM public.ecr.aws/docker/library/nginx:1.31-alpine
COPY web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/security-headers.generated.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
