# Build stage
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci || npm install
COPY . .
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
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_RAZORPAY_KEY_ID=$VITE_RAZORPAY_KEY_ID
ENV VITE_GOOGLE_SSO_ENABLED=$VITE_GOOGLE_SSO_ENABLED
ENV VITE_ONEOPS_URL=$VITE_ONEOPS_URL
RUN npm run build
# The CSP names a hash for each inline script, and the hash has to come from the minified artifact
# rather than the source, so it is computed here and not checked in.
RUN node scripts/generate-security-headers.mjs dist security-headers.conf.template security-headers.generated.conf

# Production stage
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/security-headers.generated.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
