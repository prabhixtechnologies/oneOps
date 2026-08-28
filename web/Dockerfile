# Build stage
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci || npm install
COPY . .
# Vite inlines import.meta.env.VITE_* at build time, so every var the app reads has to be declared
# here — an arg passed by Compose but not declared in this stage is silently discarded.
ARG VITE_API_URL=http://localhost:8080
ARG VITE_RAZORPAY_KEY_ID=
ARG VITE_GOOGLE_SSO_ENABLED=false
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_RAZORPAY_KEY_ID=$VITE_RAZORPAY_KEY_ID
ENV VITE_GOOGLE_SSO_ENABLED=$VITE_GOOGLE_SSO_ENABLED
RUN npm run build

# Production stage
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
