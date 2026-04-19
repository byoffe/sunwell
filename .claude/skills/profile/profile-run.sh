#!/usr/bin/env bash
# profile-run.sh — SSH into target, run JMH benchmark JAR with the given profiler flag
#
# Usage: profile-run.sh <host> <port> <user> <key> <remote-path> <jar-filename> <run-id> <profiler-flag>
#
# <profiler-flag> is the complete JMH -prof argument, constructed by the skill:
#   JFR:            "jfr:dir=/tmp/<run-id>"
#   async-profiler: "async:libPath=/opt/async-profiler/lib/libasyncProfiler.so;event=<event>;output=jfr;dir=/tmp/<run-id>"
#
# JMH writes per-benchmark recordings under /tmp/<run-id>/<benchmark-class>-<mode>/.
# Filename depends on profiler: profile.jfr (JFR) or jfr-<event>.jfr (async-profiler).
# Call collect-ssh.sh afterwards to retrieve the directory.
#
# Exits non-zero on any failure.

set -euo pipefail

if [ $# -ne 8 ]; then
  echo "Usage: $(basename "$0") <host> <port> <user> <key> <remote-path> <jar-filename> <run-id> <profiler-flag>" >&2
  exit 1
fi

HOST="$1"
PORT="$2"
USER="$3"
KEY="$4"
REMOTE_PATH="$5"
JAR_FILENAME="$6"
RUN_ID="$7"
PROFILER_FLAG="$8"

if [[ ! "$RUN_ID" =~ ^[a-zA-Z0-9._-]+$ ]]; then
  echo "ERROR: RUN_ID must contain only alphanumeric characters, dots, underscores, and hyphens: $RUN_ID" >&2
  exit 1
fi

REMOTE_DIR="/tmp/${RUN_ID}"

# StrictHostKeyChecking=no + UserKnownHostsFile=/dev/null: intentional for ephemeral
# Docker targets where the container host key changes on rebuild.
SSH_OPTS=(-i "$KEY" -p "$PORT" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o BatchMode=yes)

# printf %q escapes values for safe interpolation into remote shell commands.
REMOTE_PATH_Q=$(printf '%q' "$REMOTE_PATH")
JAR_FILENAME_Q=$(printf '%q' "$JAR_FILENAME")
REMOTE_DIR_Q=$(printf '%q' "$REMOTE_DIR")
PROFILER_FLAG_Q=$(printf '%q' "$PROFILER_FLAG")

echo "==> Profiling $JAR_FILENAME on $USER@$HOST:$PORT"
echo "    Profiler flag: -prof $PROFILER_FLAG"
echo "    Output dir:    $REMOTE_DIR"
echo ""

# SC2029: local expansion of *_Q vars is intentional — printf %q above
# produces shell-safe strings for interpolation into the remote command.
# shellcheck disable=SC2029
ssh "${SSH_OPTS[@]}" "$USER@$HOST" "
  set -euo pipefail
  mkdir -p $REMOTE_DIR_Q
  cd $REMOTE_PATH_Q
  java -jar $REMOTE_PATH_Q/$JAR_FILENAME_Q -prof $PROFILER_FLAG_Q 2>&1 | tee $REMOTE_DIR_Q/jmh-output.txt
"

echo ""
echo "Profile complete. Recordings in $HOST:$REMOTE_DIR"
