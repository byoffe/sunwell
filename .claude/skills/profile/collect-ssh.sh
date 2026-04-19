#!/usr/bin/env bash
# collect-ssh.sh — SCP JFR recordings from remote host to local results dir
#
# Usage: collect-ssh.sh <host> <port> <user> <key> <remote-dir> <local-dir>
#
# Copies the entire <remote-dir> (containing per-benchmark JFR subdirectories)
# into <local-dir>. Creates <local-dir> if it does not exist.
# Fails with the remote path searched if no .jfr files are found.
#
# Exits non-zero on any failure.

set -euo pipefail

if [ $# -ne 6 ]; then
  echo "Usage: $(basename "$0") <host> <port> <user> <key> <remote-dir> <local-dir>" >&2
  exit 1
fi

HOST="$1"
PORT="$2"
USER="$3"
KEY="$4"
REMOTE_DIR="$5"
LOCAL_DIR="$6"

# StrictHostKeyChecking=no + UserKnownHostsFile=/dev/null: intentional for ephemeral
# Docker targets where the container host key changes on rebuild.
SSH_OPTS=(-i "$KEY" -p "$PORT" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o BatchMode=yes)
SCP_OPTS=(-i "$KEY" -P "$PORT" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o BatchMode=yes)

# printf %q escapes values for safe interpolation into remote shell commands.
REMOTE_DIR_Q=$(printf '%q' "$REMOTE_DIR")

mkdir -p "$LOCAL_DIR"

echo "==> Verifying JFR recordings at $USER@$HOST:$REMOTE_DIR..."
JFR_COUNT=$(ssh "${SSH_OPTS[@]}" "$USER@$HOST" "find $REMOTE_DIR_Q -name '*.jfr' 2>/dev/null | wc -l" | xargs)
if [ "$JFR_COUNT" -eq 0 ]; then
  echo "ERROR: No .jfr files found in $HOST:$REMOTE_DIR" >&2
  exit 1
fi
echo "    Found $JFR_COUNT recording(s)"

echo "==> Collecting recordings to $LOCAL_DIR..."
scp -r "${SCP_OPTS[@]}" "$USER@$HOST:$REMOTE_DIR/." "$LOCAL_DIR/"

JFR_FILES=$(find "$LOCAL_DIR" -name "*.jfr")
echo ""
echo "Collected:"
while IFS= read -r f; do
  echo "    $f ($(du -h "$f" | cut -f1))"
done <<< "$JFR_FILES"
