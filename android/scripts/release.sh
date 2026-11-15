#!/usr/bin/env bash
# Builds the signed release APK and copies it to apk/<team>_<app>_release.apk as the contest requires.
# Usage: android/scripts/release.sh <team-name> [app-name]
set -euo pipefail
TEAM="${1:?team name required}"
APP="${2:-드라이브스토리}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT/android"
./gradlew :app:testReleaseUnitTest :app:assembleRelease -q --console=plain
mkdir -p "$ROOT/apk"
rm -f "$ROOT"/apk/*_release.apk
cp app/build/outputs/apk/release/app-release.apk "$ROOT/apk/${TEAM}_${APP}_release.apk"
ls -la "$ROOT/apk"
