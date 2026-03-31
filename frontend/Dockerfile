# ── Stage 1: build shared package ────────────────────────────
# Context must be the monorepo root:  docker build -f frontend/Dockerfile .
FROM node:20-alpine AS shared-builder
WORKDIR /app

# Install deps (workspace-aware)
COPY package.json package-lock.json ./
COPY packages/shared/package.json packages/shared/
COPY frontend/package.json frontend/
RUN npm ci --ignore-scripts

# Build shared types/utils
COPY packages/shared/ packages/shared/
RUN npm run build -w @weekly-commit/shared

# ── Stage 2: build the Vite frontend ─────────────────────────
FROM node:20-alpine AS frontend-builder
WORKDIR /app

COPY --from=shared-builder /app /app
COPY frontend/ frontend/
RUN npm run build -w frontend

# ── Stage 3: nginx serving static bundle ─────────────────────
FROM nginx:1.27-alpine

# Use nginx's built-in envsubst template support
# Place config as a template — nginx will substitute env vars on startup
COPY frontend/nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=frontend-builder /app/frontend/dist /usr/share/nginx/html

# Default backend origin for docker-compose; override on Railway
ENV BACKEND_ORIGIN=http://backend:8080
# Only substitute BACKEND_ORIGIN — leave nginx variables ($host, $remote_addr, etc.) alone
ENV NGINX_ENVSUBST_FILTER=BACKEND_ORIGIN

EXPOSE 80
