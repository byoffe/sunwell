#!/usr/bin/env bash
# deploy.sh — build the toy-app uber JAR and deploy it to the target server
#
# Usage: scripts/deploy.sh [target]
#   target: optional, defaults to "local-docker"
#           future: accept a named perf-target YAML key
#
# Exits non-zero on any failure so callers (and the loop skill) can detect it.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

TARGET="${1:-local-docker}"

# ── Target configuration ────────────────────────────────────────────────────
# For now the only supported target is local-docker.
# Future: load from a perf-target YAML file.
case "$TARGET" in
  local-docker)
    SSH_USER="sunwell"
    SSH_HOST="localhost"
    SSH_PORT="2222"
    SSH_KEY="$REPO_ROOT/examples/docker/sunwell_dev_key"
    REMOTE_HOME="/home/sunwell"
    ;;
  *)
    echo "ERROR: unknown target '$TARGET'" >&2
    echo "Supported targets: local-docker" >&2
    exit 1
    ;;
esac

JAR="$REPO_ROOT/examples/toy-app/target/toy-app-benchmarks.jar"
# ssh uses -p (lowercase) for port; scp uses -P (uppercase)
SSH_OPTS="-i $SSH_KEY -p $SSH_PORT -o StrictHostKeyChecking=no -o BatchMode=yes"
SCP_OPTS="-i $SSH_KEY -P $SSH_PORT -o StrictHostKeyChecking=no -o BatchMode=yes"

echo "==> [1/3] Building toy-app uber JAR..."
mvn package -pl examples/toy-app --also-make -q -f "$REPO_ROOT/pom.xml"
echo "    Built: $JAR"

echo "==> [2/3] Deploying to $SSH_USER@$SSH_HOST:$SSH_PORT..."
scp $SCP_OPTS "$JAR" "$SSH_USER@$SSH_HOST:$REMOTE_HOME/toy-app-benchmarks.jar"
echo "    Deployed."

echo "==> [3/3] Verifying..."
ssh $SSH_OPTS "$SSH_USER@$SSH_HOST" "
  echo '    Java:' \$(java -version 2>&1 | head -1)
  ls -lh $REMOTE_HOME/toy-app-benchmarks.jar
"

echo ""
echo "Deploy complete. Target: $TARGET"
