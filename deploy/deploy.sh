#!/usr/bin/env bash
# Builds the server jar and installs it on the VPS, then restarts the server.
# Run from the repository root.
#
#   ./deploy/deploy.sh ubuntu@203.0.113.10 --setup   first time only
#   ./deploy/deploy.sh ubuntu@203.0.113.10           every later deploy
#
# Needs SSH access to the VPS with a key (OVHcloud emails the "ubuntu" user's
# login details, or you can add your SSH key when ordering).
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 user@vps-address [--setup]" >&2
  exit 1
fi

TARGET="$1"
SETUP="${2:-}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/server/build/libs/hexadence-server.jar"

if [[ "$SETUP" == "--setup" ]]; then
  echo "==> Setting up the VPS"
  scp "$ROOT/deploy/setup-vps.sh" "$ROOT/deploy/hexadence-server.service" "$TARGET:/tmp/"
  ssh -t "$TARGET" "sudo bash /tmp/setup-vps.sh"
fi

echo "==> Building the server jar"
(cd "$ROOT" && ./gradlew server:jar)

echo "==> Uploading and restarting"
scp "$JAR" "$TARGET:/tmp/hexadence-server.jar"
ssh -t "$TARGET" "sudo install -m 644 /tmp/hexadence-server.jar /opt/hexadence/hexadence-server.jar \
  && sudo systemctl restart hexadence-server \
  && sleep 2 \
  && sudo systemctl --no-pager status hexadence-server | head -n 12"

echo "==> Deployed. Players join with -Dhexadence.join=${TARGET#*@}:7777"
