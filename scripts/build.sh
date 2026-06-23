#!/usr/bin/env bash
#
# Build the PaintAndSeek mod jar.
#
#   scripts/build.sh            # full build (compile + tests + remapped jar)
#   scripts/build.sh <args...>  # pass extra Gradle args, e.g. -x test, --rerun
#
# The loadable mod jar lands in build/libs/paintandseek-<version>.jar (the
# *-sources.jar alongside it is source only, not the mod).

cd "$(dirname "$0")/.." || exit 1
export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home}"

if [ "$#" -ge 1 ]; then
  exec ./gradlew build "$@"
else
  exec ./gradlew build
fi
