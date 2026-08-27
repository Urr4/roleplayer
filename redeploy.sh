#!/usr/bin/env bash
# redeploy.sh — rebuild and redeploy the Roleplayer stack after a code change.
# Run on the Swarm manager Pi from the project root.
set -euo pipefail

STACK="roleplayer"
IMAGE="roleplayer:latest"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── 0. Load Pi-local secrets/config ───────────────────────────────────────────
# .env is gitignored and lives only on the Pi — it is never overwritten by a
# git pull. Put host-specific values here (ASR_URL, DISCORD_BOT_TOKEN, MinIO
# credentials, …). See .env.example for the list of supported variables.
ENV_FILE="${SCRIPT_DIR}/.env"
if [[ -f "${ENV_FILE}" ]]; then
  echo "==> Loading ${ENV_FILE} …"
  set -a
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  set +a
fi

# ── 1. Build image (ARM64 for Raspberry Pi) ───────────────────────────────────
echo "==> Building ${IMAGE} …"
docker build --platform linux/arm64 -t "${IMAGE}" "${SCRIPT_DIR}"

# ── 2. Deploy stack ───────────────────────────────────────────────────────────
echo "==> Deploying stack '${STACK}' …"
MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-roleplayer}" \
MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-roleplayer123}" \
ASR_URL="${ASR_URL:-http://Stefans-PC:9090}" \
DISCORD_BOT_TOKEN="${DISCORD_BOT_TOKEN:-}" \
docker stack deploy -c "${SCRIPT_DIR}/docker-compose.yml" "${STACK}"

# ── 3. Force the app service onto the freshly built image ────────────────────
# docker stack deploy with a ":latest" tag does NOT restart already-running
# services. Since the tag string never changes between builds, Swarm considers
# the service spec unchanged and skips rolling new tasks — --force makes it
# restart the task so the freshly built image is actually used.
#
# If a previous deploy failed its healthcheck, the service's update state can
# be stuck at "paused" — a plain --force update is then rejected/ignored by
# Swarm, so clear that first by resuming/rolling back to a clean state.
UPDATE_STATE="$(docker service inspect --format '{{.UpdateStatus.State}}' "${STACK}_app" 2>/dev/null || true)"
if [[ "${UPDATE_STATE}" == "paused" ]]; then
  echo "==> Service update was paused (likely a failed healthcheck) — rolling back first …"
  docker service rollback "${STACK}_app" || true
fi

echo "==> Updating app service to new image …"
docker service update --force --image "${IMAGE}" "${STACK}_app"

# ── 4. Verify the rollout actually converged instead of pausing again ────────
echo "==> Waiting for rollout to converge …"
for _ in $(seq 1 30); do
  STATE="$(docker service inspect --format '{{.UpdateStatus.State}}' "${STACK}_app" 2>/dev/null || echo "unknown")"
  if [[ "${STATE}" == "completed" || -z "${STATE}" ]]; then
    break
  fi
  if [[ "${STATE}" == "paused" ]]; then
    echo ""
    echo "✗ Rollout paused again — the new image is failing its healthcheck."
    echo "  Check logs with: docker service logs ${STACK}_app"
    docker stack ps "${STACK}" --no-trunc
    exit 1
  fi
  sleep 2
done

echo ""
echo "✓ Deployment complete. Reachable at: http://pi1:3002"
echo ""
docker stack ps "${STACK}" --no-trunc
