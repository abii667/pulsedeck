#!/usr/bin/env bash
set -euo pipefail

MODULE="${1:-app}"

./gradlew ":${MODULE}:assembleDebug" ":${MODULE}:testDebugUnitTest"
