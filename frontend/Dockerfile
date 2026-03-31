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

# Pass API URL at build time — Railway sets this via service env vars
ARG VITE_API_BASE_URL="/api"
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

COPY --from=shared-builder /app /app
COPY frontend/ frontend/
RUN npm run build -w frontend

# ── Stage 3: lightweight static file server ──────────────────
FROM node:20-alpine

RUN npm install -g serve@14

COPY --from=frontend-builder /app/frontend/dist /app/dist

ENV PORT=80
EXPOSE 80

CMD sh -c "serve -s /app/dist -l tcp://0.0.0.0:$PORT"
