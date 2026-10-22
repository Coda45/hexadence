#!/usr/bin/env bash
# One-time setup of a fresh Ubuntu VPS for the Hexadence server.
# deploy.sh --setup uploads and runs this for you; you can also run it on the
# VPS directly with: sudo bash setup-vps.sh
#
# It installs Java 21, creates an unprivileged "hexadence" user, installs the
# systemd service, and opens the firewall for SSH and the game port.
set -euo pipefail

GAME_PORT="${GAME_PORT:-7777}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [[ $EUID -ne 0 ]]; then
  echo "Run this with sudo." >&2
  exit 1
fi

echo "==> Installing Java 21"
apt-get update -q
apt-get install -y -q openjdk-21-jre-headless ufw

echo "==> Creating the hexadence user and /opt/hexadence"
id hexadence &>/dev/null || useradd --system --no-create-home --shell /usr/sbin/nologin hexadence
install -d -o root -g root -m 755 /opt/hexadence

echo "==> Installing the systemd service"
install -m 644 "$SCRIPT_DIR/hexadence-server.service" /etc/systemd/system/hexadence-server.service
sed -i "s/^Environment=HEXADENCE_PORT=.*/Environment=HEXADENCE_PORT=$GAME_PORT/" \
  /etc/systemd/system/hexadence-server.service
systemctl daemon-reload
systemctl enable hexadence-server

echo "==> Opening the firewall for SSH and port $GAME_PORT"
# Allow SSH first, or enabling the firewall would lock you out.
ufw allow OpenSSH
ufw allow "$GAME_PORT/tcp"
# Ready for when the game adds UDP for movement:
# ufw allow "$GAME_PORT/udp"
ufw --force enable

echo "==> Done. Deploy the jar with deploy.sh, then check: systemctl status hexadence-server"
