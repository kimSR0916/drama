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

tap "사라진 연구원"
expect "시작하기"
tap "시작하기"
expect "목적지까지 얼마나 걸리나요?"
tap "15분"
expect "풀 에피소드"
shot 2-plan
tap "주행 시작"
regulation on
expect "연구소 마지막 신호가 잡혔습니다"
expect "우회 도로로 빠진다" 40
shot 3-choice
tap "우회 도로로 빠진다"
expect "샛길로 진입합니다"
tap "급제동"
expect "단말기 해독률"
tap "정체 발생"
expect "도로가 완전히 막혔군요"
shot 4-driving
expect "P단 체결 시 리포트" 120
shot 5-arrived
tap "기어 P"
regulation off
expect "오늘의 주행 결과"
expect "보상 카드"
shot 6-report
tap "이전 대화 기록"
expect "나(운전자)"
shot 7-log
tap "닫기"
tap "처음으로"
tap "사라진 연구원"
expect "이어서 하시겠습니까?"
tap "예"
tap "8분"
tap "주행 시작"
expect "접선지에서 받은 열쇠"
tap "에어백 전개"
sleep 2
"$ADB" shell dumpsys activity activities | grep -q "topResumedActivity=.*com.drama.app" && { echo "FAIL: app stayed in front after SOS" >&2; exit 1; }
echo "ok: handed off to system SOS"
"$ADB" shell am start -n com.drama.app/.MainActivity >/dev/null
expect "긴급 상황이 감지되어"
shot 8-sos-locked
echo "e2e passed; screenshots in $OUT"
