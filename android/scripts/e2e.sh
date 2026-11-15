#!/usr/bin/env bash
# Drives the installed app on a running Pleos Connect emulator through one trip.
# Gear changes go through the in-app signal panel; on the emulator, real gear input comes from the Pleos Virtual Console.
# Usage: scripts/e2e.sh [screenshot-dir]
OUT="${1:-build/e2e}"
source "$(dirname "$0")/lib.sh"

"$ADB" logcat -c -b crash
fresh
regulation off
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
expect "지난 이야기: 제1 접선지에서"
tap "에어백 전개"
expect "긴급 상황이 감지되어"
grep -q 'text="기어 P"' <<<"$(ui)" && { echo "FAIL: controls still reachable after SOS" >&2; exit 1; }
echo "ok: session locked with no controls"
shot 8-sos-locked
crashed
echo "e2e passed; screenshots in $OUT"
