# Shared helpers for the emulator scripts. Source this file; it expects OUT to be set.
set -euo pipefail
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
mkdir -p "$OUT"

ui() { "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null && "$ADB" shell cat /sdcard/ui.xml; }

tap() {
  local label bounds
  label=$(printf '%s' "$1" | sed 's/[][().*+?^$|\\{}]/\\&/g')
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


PERMS="android.permission.RECORD_AUDIO android.car.permission.CAR_SPEED"

grant() { for p in $PERMS; do "$ADB" shell pm grant --user current com.drama.app "$p" 2>/dev/null || true; done; }
launch() { "$ADB" shell am start -n com.drama.app/.MainActivity >/dev/null; }

fresh() {
  "$ADB" shell am force-stop com.drama.app
  "$ADB" shell pm clear --user current com.drama.app >/dev/null
  grant
  launch
  expect "오늘은 어떤 이야기를 달릴까요?"
}

absent() {
  ui | grep -q "$1" && { echo "FAIL: '$1' should not be on screen" >&2; exit 1; }
  echo "ok: no $1"
}

crashed() {
  "$ADB" logcat -d -b crash | grep -q "com.drama.app" && { "$ADB" logcat -d -b crash | tail -30 >&2; echo "FAIL: app crashed" >&2; exit 1; }
  echo "ok: no crash"
}
