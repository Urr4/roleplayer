#!/usr/bin/env bash
# setup.sh — first-time setup & deployment of the Roleplayer stack.
#
# Run on the Swarm manager Pi as a user with sudo rights (e.g. stefan@pi1).
# Safe to run multiple times — every step is idempotent.
#
# Usage:
#   chmod +x setup.sh
#   ./setup.sh
#
# Options:
#   ./setup.sh --wipe-db     Delete the SQLite database (warning: all sessions gone!)
#   ./setup.sh --wipe-minio  Delete all MinIO data (warning: all PDFs gone!)
#   ./setup.sh --wipe-tls    Regenerate the self-signed TLS certificate
#
# What this script does:
#   1. Install packages (docker, nfs-common) if missing
#   2. Mount the NFS share if not already mounted
#   3. Create the two NAS data directories and set permissions
#   4. Generate a self-signed TLS certificate (for browser microphone access)
#   5. Initialize the Docker Swarm if not already active
#   6. Build the image (ARM64 for Raspberry Pi)
#   7. Deploy the stack

set -euo pipefail

# ── Configuration ─────────────────────────────────────────────────────────────
NAS_IP="${NAS_IP:-192.168.178.62}"
NAS_EXPORT="/volume1/cloudstorage"
MOUNT_POINT="/volume1/cloudstorage"
DATA_ROOT="${MOUNT_POINT}/docker-swarm-data"

IMAGE="roleplayer:latest"
STACK="roleplayer"

# MinIO credentials — override as needed
MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-roleplayer}"
MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-roleplayer123}"

# Hostname/IP the Pi is reachable under on the LAN — becomes a Subject
# Alternative Name in the self-signed TLS certificate (see setup_tls()).
TLS_HOSTNAME="${TLS_HOSTNAME:-pi1}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── Helpers ────────────────────────────────────────────────────────────────────
info()  { echo -e "\033[1;34m[INFO]\033[0m  $*"; }
ok()    { echo -e "\033[1;32m[ OK ]\033[0m  $*"; }
warn()  { echo -e "\033[1;33m[WARN]\033[0m  $*"; }
die()   { echo -e "\033[1;31m[FAIL]\033[0m  $*" >&2; exit 1; }

# ─────────────────────────────────────────────────────────────────────────────
# 1. Install packages
# ─────────────────────────────────────────────────────────────────────────────
install_packages() {
  info "Checking required packages…"

  local pkgs=()
  command -v docker &>/dev/null || pkgs+=(docker.io)
  command -v git    &>/dev/null || pkgs+=(git)
  dpkg -l nfs-common &>/dev/null 2>&1 || pkgs+=(nfs-common)

  if [[ ${#pkgs[@]} -gt 0 ]]; then
    info "Installing: ${pkgs[*]}"
    sudo apt-get update -qq
    sudo apt-get install -y "${pkgs[@]}"
    ok "Packages installed."
  else
    ok "All packages present."
  fi

  if ! groups | grep -q docker; then
    info "Adding $USER to the docker group…"
    sudo usermod -aG docker "$USER"
    warn "Group added — you may need to log out/in if docker commands fail."
  fi

  if ! sudo systemctl is-active --quiet docker; then
    info "Starting the Docker daemon…"
    sudo systemctl enable --now docker
  fi

  if ! docker info --format '{{.Swarm.LocalNodeState}}' 2>/dev/null | grep -q active; then
    info "Initializing Docker Swarm (single-node manager)…"
    docker swarm init || warn "Swarm init failed — maybe already in a swarm."
  fi
}

# ─────────────────────────────────────────────────────────────────────────────
# 2. Mount the NFS share
# ─────────────────────────────────────────────────────────────────────────────
setup_nfs() {
  info "Checking NFS mount at ${MOUNT_POINT}…"

  if [[ ! -d "${MOUNT_POINT}" ]]; then
    info "Creating mount point ${MOUNT_POINT}…"
    sudo mkdir -p "${MOUNT_POINT}"
  fi

  if mountpoint -q "${MOUNT_POINT}"; then
    ok "NFS already mounted at ${MOUNT_POINT}."
    return
  fi

  local fstab_entry="${NAS_IP}:${NAS_EXPORT} ${MOUNT_POINT} nfs nfsvers=4,hard,intr,noac,_netdev,nofail 0 0"
  if ! grep -qF "${NAS_IP}:${NAS_EXPORT}" /etc/fstab; then
    info "Adding NFS entry to /etc/fstab…"
    echo "${fstab_entry}" | sudo tee -a /etc/fstab > /dev/null
    ok "fstab entry added."
  fi

  info "Mounting NFS share…"
  sudo mount -a
  if mountpoint -q "${MOUNT_POINT}"; then
    ok "NFS mounted successfully."
  else
    die "NFS mount failed. Is ${NAS_IP} reachable and the export present?"
  fi
}

# ─────────────────────────────────────────────────────────────────────────────
# 3. Create data directories and set permissions
# ─────────────────────────────────────────────────────────────────────────────
setup_directories() {
  info "Creating data directories on the NAS…"

  # Synology root_squash: containers (running as root) can't chmod themselves.
  # Permissions must be set from the Pi via sudo.
  for dir in roleplayer minio/roleplayer; do
    local path="${DATA_ROOT}/${dir}"
    if [[ ! -d "${path}" ]]; then
      info "Creating ${path}…"
      sudo mkdir -p "${path}"
    fi
    local perms
    perms=$(sudo stat -c "%a" "${path}")
    if [[ "${perms}" != "777" ]]; then
      info "Setting permissions on ${path} (was ${perms})…"
      sudo chmod 777 "${path}"
    fi
  done

  ok "Data directories ready."
}

# ─────────────────────────────────────────────────────────────────────────────
# 4. Generate a self-signed TLS certificate
# ─────────────────────────────────────────────────────────────────────────────
# Browser microphone access (getUserMedia, used for the in-browser recording
# feature) only works over a "secure context" — plain http://pi1:3002 doesn't
# qualify, no matter what permissions are granted. The app therefore also
# starts an HTTPS listener (port 3502) whenever a certificate is present here.
# On first visit to https://pi1:3502 the browser shows a one-time self-signed
# certificate warning — accept it ("Advanced" -> "Proceed anyway"), then the
# microphone works.
setup_tls() {
  local tls_dir="${DATA_ROOT}/roleplayer/tls"
  local cert="${tls_dir}/cert.pem"
  local key="${tls_dir}/key.pem"

  sudo mkdir -p "${tls_dir}"
  # Same reasoning as the data directories: Synology root_squash maps the
  # container's root user to an unprivileged anonymous user, so the directory
  # itself (not just the files) must be world-readable/searchable, otherwise
  # the container can't access the certificate after a restart ("No TLS
  # certificate found" despite the file existing).
  sudo chmod 777 "${tls_dir}"

  if [[ -f "${cert}" && -f "${key}" ]]; then
    sudo chmod 644 "${cert}" "${key}"
    ok "TLS certificate already present (${cert})."
    return
  fi

  info "Generating self-signed TLS certificate for '${TLS_HOSTNAME}'…"

  local lan_ip
  lan_ip="$(hostname -I 2>/dev/null | awk '{print $1}')"
  local san="DNS:${TLS_HOSTNAME},DNS:localhost,IP:127.0.0.1"
  [[ -n "${lan_ip}" ]] && san="${san},IP:${lan_ip}"

  sudo openssl req -x509 -newkey rsa:2048 -sha256 -days 3650 -nodes \
    -keyout "${key}" -out "${cert}" \
    -subj "/CN=${TLS_HOSTNAME}" \
    -addext "subjectAltName=${san}" \
    >/dev/null 2>&1

  sudo chmod 644 "${cert}" "${key}"

  ok "TLS certificate created: ${cert}"
}

wipe_tls() {
  local tls_dir="${DATA_ROOT}/roleplayer/tls"
  warn "Deleting existing TLS certificate at ${tls_dir}…"
  sudo rm -f "${tls_dir}/cert.pem" "${tls_dir}/key.pem"
  setup_tls
  if docker service ls --format '{{.Name}}' | grep -q "${STACK}_app"; then
    info "Restarting app service…"
    docker service update --force "${STACK}_app"
    ok "Service restarted."
  fi
}

# ─────────────────────────────────────────────────────────────────────────────
# 5. Build the image
# ─────────────────────────────────────────────────────────────────────────────
build_image() {
  info "Building ${IMAGE} for linux/arm64 (can take ~5–10 min on the Pi)…"
  docker build --platform linux/arm64 -t "${IMAGE}" "${SCRIPT_DIR}"
  ok "Image built."
}

# ─────────────────────────────────────────────────────────────────────────────
# 6. Deploy the stack
# ─────────────────────────────────────────────────────────────────────────────
deploy_stack() {
  info "Deploying Docker Swarm stack '${STACK}'…"

  MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY}" \
  MINIO_SECRET_KEY="${MINIO_SECRET_KEY}" \
  docker stack deploy -c "${SCRIPT_DIR}/docker-compose.yml" "${STACK}"

  ok "Stack deployed."

  info "Waiting for services…"
  sleep 5
  docker stack ps "${STACK}" --no-trunc
}

# ─────────────────────────────────────────────────────────────────────────────
# Wipe mode
# ─────────────────────────────────────────────────────────────────────────────
wipe_db() {
  warn "WIPE MODE: This deletes the SQLite database (all sessions, players, characters, NPCs)!"
  read -rp "Are you sure? Type YES to confirm: " confirm
  [[ "${confirm}" == "YES" ]] || { info "Aborted."; exit 0; }

  local db="${DATA_ROOT}/roleplayer/roleplayer.db"
  if [[ -f "${db}" ]]; then
    sudo rm "${db}"
    ok "Database deleted: ${db}"
  else
    warn "No database found at ${db}."
  fi

  if docker service ls --format '{{.Name}}' | grep -q "${STACK}_app"; then
    info "Restarting app service…"
    docker service update --force "${STACK}_app"
    ok "Service restarted."
  fi
}

wipe_minio() {
  warn "WIPE MODE: This deletes all MinIO data (all character sheet PDFs)!"
  read -rp "Are you sure? Type YES to confirm: " confirm
  [[ "${confirm}" == "YES" ]] || { info "Aborted."; exit 0; }

  local minio_dir="${DATA_ROOT}/minio/roleplayer"
  sudo rm -rf "${minio_dir:?}/"*
  ok "MinIO data deleted."

  if docker service ls --format '{{.Name}}' | grep -q "${STACK}_minio"; then
    docker service update --force "${STACK}_minio"
    ok "MinIO service restarted."
  fi
}

print_next_steps() {
  echo ""
  echo "════════════════════════════════════════════════════════════════════"
  echo "  Deployment complete!"
  echo "════════════════════════════════════════════════════════════════════"
  echo ""
  echo "  App reachable at:"
  echo "    http://pi1:3002   (no microphone access - browsers block"
  echo "                       getUserMedia outside HTTPS/localhost)"
  echo "    https://pi1:3502  (self-signed cert, needed for microphone"
  echo "                       recording; accept the one-time browser"
  echo "                       certificate warning)"
  echo ""
  echo "  Useful commands:"
  echo "    docker stack ps ${STACK}              # Service status"
  echo "    docker service logs ${STACK}_app      # App logs"
  echo "    docker service logs ${STACK}_minio     # MinIO logs"
  echo "    ./redeploy.sh                          # After code changes"
  echo ""
}

# ─────────────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────────────
main() {
  case "${1:-}" in
    --wipe-db)    wipe_db;    exit 0 ;;
    --wipe-minio) wipe_minio; exit 0 ;;
    --wipe-tls)   wipe_tls;   exit 0 ;;
  esac

  echo ""
  echo "════════════════════════════════════════════════════════════════════"
  echo "  Roleplayer — First-time Setup & Deployment"
  echo "════════════════════════════════════════════════════════════════════"
  echo ""

  if ! sudo -n true 2>/dev/null; then
    warn "This script needs sudo for some steps. You may be prompted for your password."
  fi

  install_packages
  setup_nfs
  setup_directories
  setup_tls
  build_image
  deploy_stack
  print_next_steps
}

main "$@"
