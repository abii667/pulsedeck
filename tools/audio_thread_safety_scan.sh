#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-app/src/main/cpp}"

if [[ ! -d "$ROOT" ]]; then
  echo "Native source root not found: $ROOT"
  exit 0
fi

grep -RIn --include='*.cpp' --include='*.cc' --include='*.cxx' --include='*.h' --include='*.hpp' \
  -E 'new |delete |malloc\(|free\(|std::mutex|lock_guard|unique_lock|condition_variable|\.wait\(|\.resize\(|__android_log|LOG[DIWE]\(|fopen\(|read\(|write\(|sleep\(|usleep\(|std::this_thread::sleep|std::string|JNIEnv|Call.*Method' "$ROOT" || true

echo "Review matches manually. Matches are allowed outside the audio callback path."
