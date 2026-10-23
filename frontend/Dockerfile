# syntax=docker/dockerfile:1.7
# ---------- build ----------
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ---------- Caddy recompilado (Go e golang.org/x/net corrigidos; ver caddy-build/main.go) ----------
FROM golang:1.27.3-alpine AS caddy
WORKDIR /src
COPY caddy-build/go.mod caddy-build/go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod go mod download
COPY caddy-build/main.go ./
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 GOTOOLCHAIN=local go build -trimpath -ldflags "-s -w" -o /caddy .

# ---------- runtime (Caddy servindo estáticos + proxy para o backend) ----------
FROM caddy:2.11.7-alpine
COPY --from=caddy /caddy /usr/bin/caddy
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv
RUN caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
USER 1000:1000
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s CMD wget -qO- http://127.0.0.1:8080/ >/dev/null || exit 1
