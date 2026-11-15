#!/usr/bin/env bash
# Exercises the AI features on the emulator against scripts/fake-llm.py: settings, a wrong key,
# scenario generation with one validation retry, generated reaction and sub-scene lines, and talk.
# Usage: scripts/e2e-ai.sh [screenshot-dir]
OUT="${1:-build/e2e-ai}"
source "$(dirname "$0")/lib.sh"
PORT=8787

python3 "$(dirname "$0")/fake-llm.py" "$PORT" > "$OUT/fake-llm.log" 2>&1 &
SERVER=$!
trap 'kill $SERVER 2>/dev/null' EXIT
sleep 1

type_into() { tap "$1"; "$ADB" shell input text "$2"; sleep 1; }

"$ADB" logcat -c -b crash
fresh
expect "AI 꺼짐"

echo "== without a key the app points to settings"
tap "내 시나리오 만들기"
expect "AI 설정 열기"
tap "AI 설정 열기"
expect "현재 상태: 꺼짐"

echo "== a rejected key is reported when generating"
type_into "API 키" "bad-key"
type_into "서버 주소 (비워 두면 기본값)" "http://10.0.2.2:$PORT"
tap "저장"
expect "AI 켜짐"
tap "닫기"
type_into "한 줄 아이디어" "ambulance"
tap "AI로 생성"
expect "API 키가 올바르지 않습니다." 20

echo "== scenario generation validates, retries, and saves"
tap "닫기"
tap "AI 설정"
type_into "API 키" "test-key"
tap "저장"
tap "닫기"
tap "내 시나리오 만들기"
type_into "한 줄 아이디어" "ambulance"
tap "AI로 생성"
expect "내 테마" 30
expect "새벽의 구급차"
grep -c "/v1/messages" "$OUT/fake-llm.log" | grep -q "^3$" || { echo "FAIL: expected 3 LLM calls (1 rejected key, 1 invalid draft, 1 valid)" >&2; cat "$OUT/fake-llm.log" >&2; exit 1; }
echo "ok: one retry after the rule-breaking draft"
shot 1-generated

echo "== generated lines replace reactions and sub scenes, choices stay scripted"
pick "새벽의 구급차"
tap "스토리 재생"
tap "15분"
tap "이 목적지로 설정"
expect "기사님, 환자를 태웠어요"
sleep 3
tap "급제동"
expect "(AI) 괜찮으세요? 놀라셨죠"
expect "골목으로 우회" 90
tap "골목으로 우회"
expect "골목길로 돌아갈게요"
shot 2-driving

echo "== talking to the character"
tap "말 걸기"
expect "(AI) 안전한 곳으로 가는 중이에요" 20
expect "주차 후 확인해 주세요" 120
tap "기어 P"
expect "오늘의 주행 결과"
tap "이전 대화 기록"
expect "우리 지금 어디로 가는 거야?"
expect "(AI) 지금까지 정말 잘 오고 있어요"
shot 3-log
crashed
echo "ai passed; screenshots in $OUT"
