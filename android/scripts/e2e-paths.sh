#!/usr/bin/env bash
# Covers the paths e2e.sh does not: every shipped story on a short trip, button, voice-timeout and
# congestion answers, restart, declining both prompts, a finished story, a custom scenario,
# a relaunch mid-drive, and an install without pre-granted permissions.
# Usage: scripts/e2e-paths.sh [screenshot-dir] [apk]
OUT="${1:-build/e2e-paths}"
APK="${2:-}"
source "$(dirname "$0")/lib.sh"

short_trip() {
  local title="$1" detour="$2" answer="$3" reply="$4" closing="$5"
  echo "== $title ($answer)"
  tap "$title"
  tap "시작하기"
  tap "8분"
  expect "핵심 장면 압축"
  tap "주행 시작"
  expect "$detour" 60
  case "$answer" in
    detour) tap "$detour" ;;
    keep) tap "기존 경로 유지" ;;
    timeout) ;;
  esac
  expect "P단 체결 시 리포트" 120
  tap "기어 P"
  expect "오늘의 주행 결과"
  tap "이전 대화 기록"
  expect "$reply"
  expect "$closing"
  tap "닫기"
  tap "처음으로"
  expect "오늘은 어떤 이야기를 달릴까요?"
}

"$ADB" logcat -c -b crash
fresh
short_trip "까칠한 황실 호위기사와 전속 마부" "우회로로 빠진다" keep "배짱 하나는 인정하지" "오늘은 여기까지다"
short_trip "사라진 연구원" "우회 도로로 빠진다" timeout "응답이 없군요. 원래 경로를 유지합니다." "1차 암호 해독 완료"
short_trip "폭주 기관차 777" "우회 선로로" detour "선로 전환기를 당겼어요" "레버가 반쯤 움직였어요"
short_trip "이세계 트럭 기사" "숲길로 우회" timeout "안전한 왕도로 그대로 갈게요" "알에 금이 가기 시작했어요"
short_trip "심야 택시: 수상한 손님" "시계탑으로 우회" keep "가던 길이 제일 안전하죠" "오늘은 여기까지 하죠"
crashed

echo "== declining both returns to select, restart opens from the beginning"
tap "사라진 연구원"
expect "이어서 하시겠습니까?"
tap "아니오"
expect "처음부터 시작하시겠습니까?"
tap "아니오"
expect "오늘은 어떤 이야기를 달릴까요?"
tap "사라진 연구원"
tap "아니오"
tap "예"
tap "8분"
tap "주행 시작"
expect "연구소 마지막 신호가 잡혔습니다"
tap "기어 P"
expect "오늘의 주행 결과"
tap "처음으로"

echo "== finished single-chapter story offers a new start"
tap "심야 택시: 수상한 손님"
expect "이어서 하시겠습니까?"
tap "예"
tap "32분"
expect "풀 에피소드"
tap "주행 시작"
expect "지난 이야기: 손님은"
tap "정체 발생"
expect "P단 체결 시 리포트" 240
tap "기어 P"
expect "100%"
tap "처음으로"
tap "심야 택시: 수상한 손님"
expect "시작하기"
absent "이어서 하시겠습니까?"
tap "뒤로"

echo "== relaunch mid-drive returns to select with progress intact"
tap "폭주 기관차 777"
expect "이어서 하시겠습니까?"
tap "예"
tap "32분"
tap "주행 시작"
expect "지난 이야기: 녹슨"
"$ADB" shell am force-stop com.drama.app
launch
expect "오늘은 어떤 이야기를 달릴까요?"
tap "폭주 기관차 777"
expect "이어서 하시겠습니까?"
tap "아니오"
tap "아니오"

echo "== custom scenario from the authoring screen"
tap "내 시나리오 만들기"
type_into() { tap "$1"; "$ADB" shell input text "$2"; sleep 1; }
tap "저장"
expect "제목을(를) 입력해 주세요."
type_into "제목" "MyStory"
type_into "캐릭터 이름" "Luna"
type_into "캐릭터 말투" "calm"
type_into "세계관" "A%sshort%sworld"
type_into "1장 제목" "Ch1"
type_into "도입 대사" "Here%swe%sgo"
type_into "장면 대사 (한 줄에 하나)" "First%sscene"
tap "저장"
expect "오늘은 어떤 이야기를 달릴까요?"
tap "MyStory"
tap "시작하기"
tap "8분"
tap "주행 시작"
expect "Here we go"
expect "First scene" 60
expect "P단 체결 시 리포트" 120
tap "기어 P"
expect "Ch1 기념 카드"
tap "처음으로"
crashed

if [ -n "$APK" ]; then
  echo "== install without pre-granted permissions"
  "$ADB" uninstall com.drama.app >/dev/null
  "$ADB" install "$APK" | tail -1
  "$ADB" logcat -c -b crash
  launch
  sleep 3
  for _ in 1 2 3 4; do
    grep -q 'text="허용 안함"' <<<"$(ui)" || break
    tap "허용 안함"
  done
  expect "오늘은 어떤 이야기를 달릴까요?"
  tap "사라진 연구원"
  tap "시작하기"
  tap "8분"
  tap "주행 시작"
  expect "우회 도로로 빠진다" 60
  tap "우회 도로로 빠진다"
  expect "P단 체결 시 리포트" 120
  tap "기어 P"
  expect "오늘의 주행 결과"
  crashed
  grant
fi
echo "paths passed; screenshots in $OUT"
