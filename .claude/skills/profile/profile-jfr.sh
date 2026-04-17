#!/usr/bin/env bash
# profile-jfr.sh — SSH into target, run JMH benchmark JAR with JFR attached
#
# Usage: profile-jfr.sh <host> <port> <user> <key> <remote-path> <jar-filename> <duration> <run-id>
#
# Leaves the JFR recording at /tmp/<run-id>.jfr on the remote host.
# Call collect-ssh.sh afterwards to retrieve it.
#
# JDK compatibility:
#   JDK 11–12: -XX:+FlightRecorder is valid but unnecessary; omitted here
#   JDK 13+:   -XX:+FlightRecorder is deprecated; StartFlightRecording alone suffices
#   JDK 8:     requires -XX:+UnlockCommercialFeatures -XX:+FlightRecorder (not supported)
# Future: detect remote JDK version and adjust flags accordingly.
#
# Exits non-zero on any failure.

set -euo pipefail

HOST="$1"
PORT="$2"
USER="$3"
KEY="$4"
REMOTE_PATH="$5"
JAR_FILENAME="$6"
DURATION="$7"
RUN_ID="$8"

REMOTE_RECORDING="/tmp/${RUN_ID}.jfr"

SSH_OPTS="-i $KEY -p $PORT -o StrictHostKeyChecking=no -o BatchMode=yes"

echo "==> Profiling $JAR_FILENAME on $USER@$HOST:$PORT"
echo "    JFR duration: ${DURATION}s  |  recording: $REMOTE_RECORDING"
echo ""

ssh $SSH_OPTS "$USER@$HOST" "
  set -euo pipefail
  cd $REMOTE_PATH
  java \
    -XX:StartFlightRecording=duration=${DURATION}s,filename=${REMOTE_RECORDING},settings=profile \
    -jar $REMOTE_PATH/$JAR_FILENAME
"

echo ""
echo "Profile complete. Recording at $HOST:$REMOTE_RECORDING"
