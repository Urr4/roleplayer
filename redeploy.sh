#!/usr/bin/env bash
# redeploy.sh — rebuild and redeploy the Roleplayer stack after a code change.
# Run on the Swarm manager Pi from the project root.
set -euo pipefail

STACK="roleplayer"
IMAGE="roleplayer:latest"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── 1. Build image (ARM64 for Raspberry Pi) ───────────────────────────────────
echo "==> Building ${IMAGE} …"
docker build --platform linux/arm64 -t "${IMAGE}" "${SCRIPT_DIR}"

# ── 2. Deploy stack ───────────────────────────────────────────────────────────
echo "==> Deploying stack '${STACK}' …"
MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-roleplayer}" \
MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-roleplayer123}" \
docker stack deploy -c "${SCRIPT_DIR}/docker-compose.yml" "${STACK}"

# ── 3. Force the app service onto the freshly built image ────────────────────
# docker stack deploy with a ":latest" tag does NOT restart already-running
# services. Since the tag string never changes between builds, Swarm considers
# the service spec unchanged and skips rolling new tasks — --force makes it
# restart the task so the freshly built image is actually used.
echo "==> Updating app service to new image …"
docker service update --force --image "${IMAGE}" "${STACK}_app"

echo ""
echo "✓ Deployment complete. Reachable at: http://pi1:3002"
echo ""
docker stack ps "${STACK}" --no-trunc
