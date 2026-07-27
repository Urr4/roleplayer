#!/usr/bin/env bash
# run-local.sh — run the Roleplayer stack locally with no external services.
#
# Starts the Spring Boot backend under the "local" profile (in-memory stub
# repositories instead of SQLite, in-memory PDF store instead of MinIO — see
# adapter/memory/ and application-local.properties) and the Vite frontend dev
# server, side by side. All data is volatile and reset whenever the backend
# restarts — this is purely for quick local dev/demo purposes, not for
# testing the real deployment (use docker-compose.yml for that).
#
# Usage:
#   ./run-local.sh
#
# Stops both processes on Ctrl+C.

set -euo pipefail
cd "$(dirname "$0")"

BACKEND_PORT="${PORT:-3002}"
FRONTEND_PID=""
BACKEND_PID=""

cleanup() {
  echo
  echo "Stopping local dev servers…"
  [[ -n "$FRONTEND_PID" ]] && kill "$FRONTEND_PID" 2>/dev/null || true
  [[ -n "$BACKEND_PID" ]] && kill "$BACKEND_PID" 2>/dev/null || true
  wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo "==> Starting backend (Spring Boot, profile=local, port $BACKEND_PORT)…"
PORT="$BACKEND_PORT" ./gradlew bootRun \
  -x installFrontend -x buildFrontend -x copyFrontend \
  --args="--spring.profiles.active=local" &
BACKEND_PID=$!

echo "==> Installing frontend dependencies (if needed)…"
(cd frontend && [[ -d node_modules ]] || npm install)

echo "==> Starting frontend (Vite dev server, proxying /api -> http://localhost:$BACKEND_PORT)…"
(cd frontend && VITE_BACKEND_PORT="$BACKEND_PORT" npm run dev) &
FRONTEND_PID=$!

wait "$BACKEND_PID" "$FRONTEND_PID"
