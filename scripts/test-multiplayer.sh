#!/usr/bin/env bash
#
# Local multiplayer test harness for PaintAndSeek.
#
#   scripts/test-multiplayer.sh [start]   # start server + 4 clients
#   scripts/test-multiplayer.sh stop      # kill them all
#
# Starts a dedicated server (offline mode, flat world) and launches 4 dev clients
# (Alice/Bob/Carol/Dave) that auto-connect. All four are ops, so any of them can
# run /paintandseek in chat. Logs go to /tmp/paintandseek-test/.
#
# Notes:
#  - Each client uses its own --project-cache-dir so the concurrent Gradle builds
#    don't fight over the project lock.
#  - The server's stdin is held open (tail -f /dev/null) so it doesn't shut down
#    on EOF when run non-interactively.
#  - 5 JVMs (server + 4 clients) is heavy; give it RAM/time.

cd "$(dirname "$0")/.." || exit 1
PROJECT_DIR="$(pwd)"
export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home}"

PLAYERS=(Alice Bob Carol Dave)
SERVER_ADDR="127.0.0.1:25565"
LOGDIR="/tmp/paintandseek-test"

stop() {
  pkill -f "fabric-loom|runServer|runClient|net.minecraft" 2>/dev/null
  echo "Stopped server + clients."
}

if [ "${1:-start}" = "stop" ]; then stop; exit 0; fi

echo "Cleaning up any previous run..."
pkill -f "runServer|runClient|net.minecraft" 2>/dev/null
sleep 2
mkdir -p "$LOGDIR" run

# --- server config ---
printf 'eula=true\n' > run/eula.txt
cat > run/server.properties <<EOF
online-mode=false
spawn-protection=0
max-players=8
view-distance=6
level-type=minecraft\:flat
motd=PaintAndSeek test
EOF

# --- op the players (offline UUIDs) so they can run commands ---
offline_uuid() {
  python3 -c "import uuid,hashlib,sys;n=sys.argv[1];m=bytearray(hashlib.md5(('OfflinePlayer:'+n).encode()).digest());m[6]=(m[6]&0x0f)|0x30;m[8]=(m[8]&0x3f)|0x80;print(uuid.UUID(bytes=bytes(m)))" "$1"
}
{
  echo "["
  last=$(( ${#PLAYERS[@]} - 1 ))
  for i in "${!PLAYERS[@]}"; do
    n="${PLAYERS[$i]}"; u="$(offline_uuid "$n")"; sep=","; [ "$i" -eq "$last" ] && sep=""
    printf '  {"uuid":"%s","name":"%s","level":4,"bypassesPlayerLimit":false}%s\n' "$u" "$n" "$sep"
  done
  echo "]"
} > run/ops.json
echo "Wrote run/ops.json (${PLAYERS[*]} as ops)."

# --- start server, stdin held open so it doesn't stop on EOF ---
echo "Starting server (log: $LOGDIR/server.log)..."
tail -f /dev/null | ./gradlew --no-daemon runServer > "$LOGDIR/server.log" 2>&1 &
until grep -q "Done (" "$LOGDIR/server.log" 2>/dev/null; do
  if grep -qE "BUILD FAILED|Caused by:" "$LOGDIR/server.log" 2>/dev/null; then
    echo "Server failed to start — see $LOGDIR/server.log"; stop; exit 1
  fi
  sleep 2
done
echo "Server ready."

# --- launch clients ---
# Each client uses its own project-cache-dir (to dodge Gradle's lock) but they
# share the build/ output dir, so concurrent COMPILES would corrupt each other.
# Launch one at a time and wait until each is past compilation (its window is
# starting: "Setting user:") before launching the next — builds are serialized,
# but the clients then run concurrently.
for i in "${!PLAYERS[@]}"; do
  n="${PLAYERS[$i]}"; pc="build/pc$((i + 1))"
  echo "Launching $n (cache $pc, log $LOGDIR/$n.log)..."
  ./gradlew --no-daemon --project-cache-dir "$pc" runClient \
    --args="--username $n --quickPlayMultiplayer $SERVER_ADDR" > "$LOGDIR/$n.log" 2>&1 &
  until grep -q "Setting user:" "$LOGDIR/$n.log" 2>/dev/null; do
    if grep -qE "BUILD FAILED" "$LOGDIR/$n.log" 2>/dev/null; then
      echo "  $n failed to build — see $LOGDIR/$n.log"; break
    fi
    sleep 2
  done
done

echo
echo "All launched. 4 windows will open and auto-connect to $SERVER_ADDR."
echo "Any client can run:  /paintandseek newround"
echo "Stop everything with: $0 stop"
wait
