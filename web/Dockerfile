# syntax=docker/dockerfile:1
# Base images come from AWS's public gallery rather than Docker Hub. These are the same
# Docker Official Images, mirrored by AWS, and the move takes Docker Hub's anonymous pull
# limit off the build path: a rate-limited base image fails the build for a reason that has
# nothing to do with the change being built.

# Build context is the oneOps repository root so the console can file:-depend on
# packages/oneops-api. CI and Infra compose pass context: <oneOps> and this Dockerfile.

# Build stage
FROM public.ecr.aws/docker/library/node:26-alpine AS build
WORKDIR /app
# oneops-api sits at /packages/oneops-api because web/package.json depends on it as
# file:../packages/oneops-api, and that resolves relative to /app. It is generated from this
# repository's own OpenAPI spec and is not published, so it stays a link.
#
# web-kit used to be vendored here too, as `COPY --from=webkit packages ./vendor/web-kit/packages`
# followed by two `node -e` scripts that rewrote package.json to point the three file: links at
# the vendored copy. brand, ui and oidc-client are versioned dependencies now, so the lockfile
# resolves them and none of that is needed.
COPY packages/oneops-api /packages/oneops-api
# .npmrc maps the @prabhixtechnologies scope to GitHub Packages. It holds no credential; see the
# file itself.
COPY web/package.json web/package-lock.json web/.npmrc ./
# The token is mounted for this layer only. GitHub Packages demands one even though the packages
# are public, and writing it into the image would put a credential in a layer that gets pushed to
# ECR, so the file is created and deleted inside a single RUN.
RUN --mount=type=secret,id=gh_token \
    printf '//npm.pkg.github.com/:_authToken=%s\n' "$(cat /run/secrets/gh_token)" > /root/.npmrc \
 && npm ci --no-audit --no-fund \
 && rm -f /root/.npmrc
COPY web/ ./
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
# Vite inlines this, so a compose variable at container start cannot change it. CI and
# Infra/deploy/aws/build-push.ps1 pass https://api.prabhixtechnologies.com; local compose passes
# http://localhost:8081. Left blank on purpose: the old localhost default was what a production
# image shipped when CI forgot the arg, and every login then left the site for the builder's laptop.
ARG VITE_IDENTITY_ISSUER=
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
FROM public.ecr.aws/docker/library/nginx:1.27-alpine
COPY web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/security-headers.generated.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
