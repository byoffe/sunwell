#!/usr/bin/env bash
# deploy-ssh.sh — SCP a JAR to an SSH target and verify it landed
#
# Usage: deploy-ssh.sh <host> <port> <user> <key> <local-jar> <remote-path>
#
# All config is passed as arguments — this script knows nothing about
# sunwell.yml, target names, or app names. The deploy skill resolves
# config and calls this script with fully-resolved values.
#
# Exits non-zero on any failure.

set -euo pipefail

if [ $# -ne 6 ]; then
  echo "Usage: $(basename "$0") <host> <port> <user> <key> <local-jar> <remote-path>" >&2
  exit 1
fi

HOST="$1"
PORT="$2"
USER="$3"
KEY="$4"
LOCAL_JAR="$5"
REMOTE_PATH="$6"

JAR_FILENAME="$(basename "$LOCAL_JAR")"

# StrictHostKeyChecking=no + UserKnownHostsFile=/dev/null: intentional for ephemeral
# Docker targets where the container host key changes on rebuild.
SSH_OPTS=(-i "$KEY" -p "$PORT" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o BatchMode=yes)
SCP_OPTS=(-i "$KEY" -P "$PORT" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o BatchMode=yes)

# printf %q escapes values for safe interpolation into remote shell commands.
REMOTE_PATH_Q=$(printf '%q' "$REMOTE_PATH")
JAR_FILENAME_Q=$(printf '%q' "$JAR_FILENAME")

echo "==> [1/2] Deploying $JAR_FILENAME to $USER@$HOST:$PORT..."
scp "${SCP_OPTS[@]}" "$LOCAL_JAR" "$USER@$HOST:$REMOTE_PATH/$JAR_FILENAME"
echo "    Deployed."

echo "==> [2/2] Verifying..."
ssh "${SSH_OPTS[@]}" "$USER@$HOST" "
  echo '    Java:' \$(java -version 2>&1 | head -1)
  ls -lh $REMOTE_PATH_Q/$JAR_FILENAME_Q
"

echo ""
echo "Deploy complete: $USER@$HOST:$REMOTE_PATH/$JAR_FILENAME"
