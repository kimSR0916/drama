#!/usr/bin/env bash
# Drives the installed app on a running Pleos Connect emulator through one trip.
# Gear changes go through the in-app signal panel; on the emulator, real gear input comes from the Pleos Virtual Console.
# Usage: scripts/e2e.sh [screenshot-dir]
set -euo pipefail
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
OUT="${1:-build/e2e}"
mkdir -p "$OUT"

ui() { "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null && "$ADB" shell cat /sdcard/ui.xml; }

tap() {
  local label="$1" bounds
  for _ in 1 2 3 4 5; do
    bounds=$(ui | grep -oE "text=\"$label\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" | head -1 | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' || true)
    [ -n "$bounds" ] && break
    sleep 1
  done
  [ -n "$bounds" ] || { echo "FAIL: '$label' not on screen" >&2; exit 1; }
  read -r x1 y1 x2 y2 < <(echo "$bounds" | tr -c '0-9\n' ' ')
  "$ADB" shell input tap $(((x1 + x2) / 2)) $(((y1 + y2) / 2))
  sleep 1
}

expect() {
  for _ in $(seq 1 "${2:-10}"); do
    ui | grep -q "$1" && { echo "ok: $1"; return; }
    sleep 1
  done
  echo "FAIL: expected '$1'" >&2
  exit 1
}

shot() { "$ADB" exec-out screencap -p > "$OUT/$1.png"; }
regulation() { "$ADB" shell cmd hmg_car_service --service drivingstate driving_regulation "$1" >/dev/null 2>&1 || true; }

"$ADB" shell pm clear --user current com.drama.app >/dev/null
for p in android.permission.RECORD_AUDIO android.car.permission.CAR_SPEED; do
  "$ADB" shell pm grant --user current com.drama.app "$p" 2>/dev/null || true
done
regulation off
"$ADB" shell am start -n com.drama.app/.MainActivity >/dev/null
expect "오늘은 어떤 이야기를 달릴까요?"
shot 1-select

tap "야간 탐정 사무소"
expect "새로 시작"
tap "새로 시작"
expect "목적지까지 얼마나 걸리나요?"
tap "15분"
expect "풀 서사"
shot 2-plan
tap "주행 시작"
regulation on
expect "주행 제한 활성"
expect "하루: 탐정님, 첫 의뢰예요"
expect "우체국장" 30
shot 3-choice
tap "우체국장"
expect "우회 경로로 재탐색합니다."
tap "급제동"
expect "깜짝이야"
tap "정체 발생"
expect "길이 막히네요"
shot 4-driving
expect "도착했어요" 40
shot 5-arrived
tap "기어 P"
regulation off
expect "오늘의 주행 리포트"
expect "보상 카드"
shot 6-report
tap "처음으로"
tap "야간 탐정 사무소"
expect "이어서 하시겠습니까?"
tap "예"
tap "5분"
tap "주행 시작"
expect "이번엔 무도회장"
tap "에어백 전개"
expect "SOS"
shot 7-sos
echo "e2e passed; screenshots in $OUT"
