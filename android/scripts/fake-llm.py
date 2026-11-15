#!/usr/bin/env python3
"""Stand-in for the Claude Messages API so the emulator suite can exercise the app's AI paths without a key.

Usage: scripts/fake-llm.py [port]   (the emulator reaches it at http://10.0.2.2:<port>)
The first scenario request gets a rule-breaking draft so the app's validate-and-retry loop runs.
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

SCENARIO = {
    "genre": "재난·드라마",
    "title": "새벽의 구급차",
    "premise": "운전자는 정전된 도시를 달리는 구급차 기사다. 구급대원 한별과 함께 환자를 병원까지 무사히 옮긴다.",
    "persona": {
        "name": "한별", "tone": "차분하고 다정한 구급대원", "icon": "🚑",
        "bondTitles": ["처음 만난 대원", "호흡이 맞는 동료", "믿고 맡기는 파트너"],
        "reactions": {
            "harshAccel": "서두르지 않아도 돼요. 환자는 흔들리지 않는 게 제일 중요해요.",
            "harshBrake": "괜찮으세요? 환자는 무사해요. 앞을 미리 살피며 가요.",
            "harshTurn": "수액이 흔들렸어요. 커브는 부드럽게 부탁해요.",
            "congestion": "길이 막히네요. 이 틈에 환자 상태를 다시 볼게요."
        }
    },
    "chapters": [
        {
            "title": "제1장. 정전된 도시", "opening": "기사님, 환자를 태웠어요. 병원까지 천천히, 안전하게 가요.",
            "recap": "지난 이야기: 정전된 도시에서 환자를 태우고 병원으로 출발했어요.",
            "scenes": [
                {"core": False, "line": "신호등이 꺼진 교차로가 많아요. 평소보다 더 천천히 살피며 가요."},
                {"core": True, "line": "큰길에 사고가 났대요. 우회할까요, 그대로 직진할까요?",
                 "detour": {"label": "골목으로 우회", "keyword": "우회", "reply": "골목길로 돌아갈게요. 좁으니까 천천히요."},
                 "keep": {"label": "기존 경로 유지", "keyword": "직진", "reply": "큰길로 계속 가요. 경찰이 길을 터 준대요."},
                 "timeout": "대답이 없으시네요. 가던 길로 그대로 갈게요."},
                {"core": True, "line": "환자가 눈을 떴어요. 기사님 덕분에 편안하대요."}
            ],
            "cliffhanger": "병원까지는 다음 운전에서 마저 가요.", "finale": "병원에 도착했어요. 오늘도 수고하셨어요, 기사님.",
            "reward": "새벽의 구급차"
        },
        {
            "title": "제2장. 돌아가는 길", "opening": "기사님, 돌아가는 길에 호출이 하나 더 들어왔어요.",
            "recap": "지난 이야기: 첫 환자를 병원에 무사히 옮겼어요.",
            "scenes": [
                {"core": False, "line": "아까 그 환자, 수술이 잘 끝났대요. 다행이죠?"},
                {"core": True, "line": "다리 쪽이 통제 중이래요. 우회할까요, 직진해서 기다릴까요?",
                 "detour": {"label": "강변으로 우회", "keyword": "우회", "reply": "강변길로 돌아가요. 조금 멀어도 안전해요."},
                 "keep": {"label": "기존 경로 유지", "keyword": "직진", "reply": "통제가 풀릴 때까지 차분히 기다려요."},
                 "timeout": "말씀이 없으시네요. 가던 길로 갈게요."},
                {"core": True, "line": "저기 손 흔드는 분이 호출하신 분이에요. 천천히 세워 주세요."}
            ],
            "cliffhanger": "다음 운전에서 이어서 가요.", "finale": "두 번째 호출도 무사히 마쳤어요. 최고의 파트너예요.",
            "reward": "도시의 등불"
        }
    ]
}

scenario_calls = 0


def reply_for(body):
    global scenario_calls
    system = body.get("system") or ""
    if isinstance(system, list):
        system = " ".join(b.get("text", "") for b in system)
    last = body["messages"][-1]["content"]
    if isinstance(last, list):
        last = " ".join(b.get("text", "") for b in last)
    if "시나리오 작가" in system:
        scenario_calls += 1
        draft = json.loads(json.dumps(SCENARIO))
        if scenario_calls == 1:
            draft["chapters"][0]["opening"] = "가" * 90
        return json.dumps(draft, ensure_ascii=False)
    if "운전자가 방금 너에게" in last:
        return "(AI) 안전한 곳으로 가는 중이에요. 저만 믿고 천천히 가요."
    if "급제동" in last:
        return "(AI) 괜찮으세요? 놀라셨죠. 앞을 미리 살피며 천천히 가요."
    if "급가속" in last:
        return "(AI) 서두르지 않아도 돼요. 우리 속도로 가요."
    if "방향을 틀었다" in last:
        return "(AI) 커브에서는 조금만 더 부드럽게 부탁해요."
    if "막혀" in last:
        return "(AI) 길이 막히네요. 초조해하지 말고 숨을 고르죠."
    return "(AI) 지금까지 정말 잘 오고 있어요. 이대로만 가요."


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get("content-length", 0))) or b"{}")
        credential = (self.headers.get("x-api-key") or "") + (self.headers.get("authorization") or "")
        if "bad-key" in credential:
            payload, status = {"type": "error", "error": {"type": "authentication_error", "message": "invalid x-api-key"}}, 401
        else:
            payload, status = {
                "id": "msg_fake", "type": "message", "role": "assistant", "model": body.get("model", ""),
                "content": [{"type": "text", "text": reply_for(body)}],
                "stop_reason": "end_turn", "stop_sequence": None,
                "usage": {"input_tokens": 1, "output_tokens": 1},
            }, 200
        data = json.dumps(payload, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("content-type", "application/json")
        self.send_header("content-length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)
        print(self.path, status, body.get("model"), (body.get("output_config") or {}).get("effort"), "fallbacks" in body, flush=True)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    HTTPServer(("0.0.0.0", int(sys.argv[1]) if len(sys.argv) > 1 else 8787), Handler).serve_forever()
