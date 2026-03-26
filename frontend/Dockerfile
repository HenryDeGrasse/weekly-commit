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
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=frontend-builder /app/frontend/dist /usr/share/nginx/html

EXPOSE 80
