#!/usr/bin/env bash
# One-time VPS setup for automatic deploys from GitHub. Run on the VPS:
#   sudo bash setup-auto-deploy.sh /tmp/hexadence-deploy.pub
#
# It creates a "deploy" user whose SSH key can do exactly one thing: hand a
# new server jar to hexadence-receive-update. The key cannot open a shell,
# forward ports, or run any other command, so it is safe to store as a
# GitHub secret. Run it again with a new key to replace the old one.
set -euo pipefail

PUBKEY_FILE="${1:-}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RECEIVER=/usr/local/bin/hexadence-receive-update
SUDOERS=/etc/sudoers.d/hexadence-deploy

if [[ $EUID -ne 0 ]]; then
  echo "Run this with sudo." >&2
  exit 1
fi

if [[ -z "$PUBKEY_FILE" || ! -f "$PUBKEY_FILE" ]]; then
  echo "Usage: sudo bash setup-auto-deploy.sh <path to the deploy key's .pub file>" >&2
  exit 1
fi

if ! grep -qE '^ssh-(ed25519|rsa) ' "$PUBKEY_FILE"; then
  echo "$PUBKEY_FILE does not look like a public key (.pub)." >&2
  exit 1
fi

echo "==> Installing the update receiver"
apt-get install -y -q python3 >/dev/null
install -o root -g root -m 755 "$SCRIPT_DIR/hexadence-receive-update" "$RECEIVER"

echo "==> Creating the deploy user"
id deploy &>/dev/null || useradd --create-home --shell /bin/bash deploy
passwd -l deploy >/dev/null   # no password login, key only

echo "==> Allowing deploy to run only the receiver as root"
echo "deploy ALL=(root) NOPASSWD: $RECEIVER" > "$SUDOERS.tmp"
chmod 440 "$SUDOERS.tmp"
visudo -cf "$SUDOERS.tmp" >/dev/null
mv "$SUDOERS.tmp" "$SUDOERS"

echo "==> Installing the deploy key, restricted to the receiver"
install -d -o deploy -g deploy -m 700 /home/deploy/.ssh
printf 'restrict,command="sudo %s" %s\n' "$RECEIVER" "$(head -n 1 "$PUBKEY_FILE")" \
  > /home/deploy/.ssh/authorized_keys
chown deploy:deploy /home/deploy/.ssh/authorized_keys
chmod 600 /home/deploy/.ssh/authorized_keys

echo "==> Done. GitHub can now deploy with: ssh deploy@<this server> < hexadence-server.jar"
