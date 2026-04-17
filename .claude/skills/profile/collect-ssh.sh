#!/usr/bin/env bash
# collect-ssh.sh — SCP a profiling recording from remote host to local results dir
#
# Usage: collect-ssh.sh <host> <port> <user> <key> <remote-file> <local-dir>
#
# Creates <local-dir> if it does not exist.
# Produces <local-dir>/recording.<ext> preserving the source file extension.
#
# Exits non-zero on any failure.

set -euo pipefail

HOST="$1"
PORT="$2"
USER="$3"
KEY="$4"
REMOTE_FILE="$5"
LOCAL_DIR="$6"

EXT="${REMOTE_FILE##*.}"
LOCAL_RECORDING="$LOCAL_DIR/recording.$EXT"

SCP_OPTS="-i $KEY -P $PORT -o StrictHostKeyChecking=no -o BatchMode=yes"

mkdir -p "$LOCAL_DIR"

echo "==> Collecting $USER@$HOST:$REMOTE_FILE..."
scp $SCP_OPTS "$USER@$HOST:$REMOTE_FILE" "$LOCAL_RECORDING"

echo "    Saved: $LOCAL_RECORDING ($(du -h "$LOCAL_RECORDING" | cut -f1))"
