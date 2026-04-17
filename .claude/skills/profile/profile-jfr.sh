#!/usr/bin/env bash
# profile-jfr.sh — SSH into target, run JMH benchmark JAR with JFR via -prof jfr
#
# Usage: profile-jfr.sh <host> <port> <user> <key> <remote-path> <jar-filename> <run-id>
#
# JMH manages recording lifecycle per fork (measurement-only, no warmup data).
# Recordings land in /tmp/<run-id>/<benchmark-class>-<mode>/profile.jfr on the remote.
# One file per benchmark; last fork wins (acceptable — clean measurement data).
# Call collect-ssh.sh afterwards to retrieve the directory.
#
# JDK compatibility:
#   JDK 11+: JFR is built in; -prof jfr uses jcmd JFR.start/JFR.stop internally
#   JDK 8:   not supported (requires Oracle JDK commercial features)
# Future: detect remote JDK version and adjust accordingly.
#
# Exits non-zero on any failure.

set -euo pipefail

HOST="$1"
PORT="$2"
USER="$3"
KEY="$4"
REMOTE_PATH="$5"
JAR_FILENAME="$6"
RUN_ID="$7"

REMOTE_DIR="/tmp/${RUN_ID}"

SSH_OPTS="-i $KEY -p $PORT -o StrictHostKeyChecking=no -o BatchMode=yes"

echo "==> Profiling $JAR_FILENAME on $USER@$HOST:$PORT"
echo "    JFR output dir: $REMOTE_DIR"
echo ""

ssh $SSH_OPTS "$USER@$HOST" "
  set -euo pipefail
  mkdir -p $REMOTE_DIR
  cd $REMOTE_PATH
  java -jar $REMOTE_PATH/$JAR_FILENAME -prof "jfr:dir=$REMOTE_DIR" 2>&1 | tee $REMOTE_DIR/jmh-output.txt
"

echo ""
echo "Profile complete. Recordings in $HOST:$REMOTE_DIR"
