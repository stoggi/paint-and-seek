#!/usr/bin/env bash
#
# Local singleplayer test for PaintAndSeek.
#
#   scripts/singleplayer-test.sh                 # open the client to the main menu
#   scripts/singleplayer-test.sh <world-name>    # jump into an EXISTING world via Quick Play
#
# Launches ONE dev client with the mod compiled from source (runClient builds it
# first, so you always get your latest changes). Extra args after the world name
# pass through to Minecraft, e.g.
#   scripts/singleplayer-test.sh "New World" --width 1280 --height 720
#
# Notes:
#  - Quick Play opens an existing world only; it won't create one. With no world
#    name, pick Singleplayer from the menu.
#  - Runs in the foreground so Ctrl-C stops the client; output goes to this terminal.

cd "$(dirname "$0")/.." || exit 1
export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home}"

# Client options enforced each run (Minecraft rewrites options.txt on exit):
#   pauseOnLostFocus=false  -> keep running when you click away to the terminal
#   soundCategory_music=0.0 -> mute background music
set_option() {
  local key="$1" val="$2" file="run/options.txt"
  mkdir -p run; touch "$file"
  if grep -q "^${key}:" "$file"; then
    sed -i '' "s/^${key}:.*/${key}:${val}/" "$file"
  else
    printf '%s:%s\n' "$key" "$val" >> "$file"
  fi
}
set_option pauseOnLostFocus false
set_option soundCategory_music 0.0

if [ "$#" -ge 1 ]; then
  world="$1"; shift
  echo "Launching client into singleplayer world '$world'..."
  exec ./gradlew runClient --args="--quickPlaySingleplayer ${world} $*"
else
  echo "Launching client to the main menu (pick Singleplayer)..."
  exec ./gradlew runClient
fi
