# ---------- build ----------
# Avoids BuildKit-only syntax so this also builds with the classic builder.
FROM node:22-alpine AS build
WORKDIR /app

# node_modules is installed in its own layer and .dockerignore keeps the host
# copy from clobbering it.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---------- runtime ----------
FROM nginx:1.27-alpine AS runtime

# Local single-container reverse proxy. Distinct from the deployment config in
# /nginx, which expects TLS certificates and a rendered __BACKEND_UPSTREAM__.
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80