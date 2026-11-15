package com.drama.app.ai

import com.drama.app.authoring.DraftResult
import com.drama.app.authoring.parseScenarioJson
import com.drama.app.core.Scenario
import com.drama.app.core.ScenarioSource
import org.json.JSONException
import org.json.JSONObject

const val SCENARIO_MAX_TOKENS = 16_000L
const val SCENARIO_ATTEMPTS = 3
const val IDEA_MAX = 200

sealed interface WriteResult {
    data class Ok(val scenario: Scenario, val attempts: Int) : WriteResult
    data class Failed(val message: String) : WriteResult
}

private val SYSTEM = """
너는 차량용 인터랙티브 드라마 '드라마'의 시나리오 작가다. 운전자는 조수석의 캐릭터와 음성으로 이야기를 진행한다.
사용자의 한 줄 아이디어로 시나리오 하나를 써서 JSON 객체 하나만 출력한다. 설명이나 코드 블록 표시는 붙이지 않는다.

형식:
{
  "genre": "장르",
  "title": "제목 (40자 이하)",
  "premise": "세계관과 운전자의 역할 (500자 이하). 운전자가 이야기 속에서 무언가를 '운전'하는 역할이면 좋다.",
  "persona": {
    "name": "캐릭터 이름", "tone": "말투 (40자 이하)", "icon": "이모지 1개",
    "bondTitles": ["호감도 1단계 칭호", "2단계", "3단계"],
    "reactions": { "harshAccel": "급가속 반응", "harshBrake": "급제동 반응", "harshTurn": "거친 조향 반응", "congestion": "정체 반응" }
  },
  "chapters": [
    {
      "title": "제1장. 제목", "opening": "도입 대사", "recap": "지난 이야기: ... 로 시작하는 요약 대사",
      "scenes": [
        { "core": false, "line": "일상 대화나 세계관 설명" },
        { "core": true, "line": "... 우회할까요, 직진할까요?",
          "detour": { "label": "버튼 문구", "keyword": "우회", "reply": "우회를 골랐을 때 대사" },
          "keep": { "label": "기존 경로 유지", "keyword": "직진", "reply": "유지를 골랐을 때 대사" },
          "timeout": "5초 동안 답이 없어 기존 경로를 유지할 때 대사" },
        { "core": true, "line": "핵심 사건" }
      ],
      "cliffhanger": "챕터 도중 도착했을 때 다음을 기약하는 대사", "finale": "챕터를 끝냈을 때 대사", "reward": "보상 카드 이름"
    }
  ]
}

규칙:
- 챕터는 2개, 챕터마다 장면은 3~5개. 챕터마다 "core": true 장면이 하나 이상 있어야 한다.
- 캐릭터가 말하는 모든 대사(line, reply, timeout, opening, recap, cliffhanger, finale, reactions)는 공백 포함 80자 이하로, 귀로만 들어도 이해되게 쓴다.
- 선택지는 챕터마다 한 번, 반드시 detour(경로 우회)와 keep(기존 경로 유지) 두 가지다. 질문 대사(line) 안에 두 keyword가 글자 그대로 들어 있어야 운전자가 음성으로 답할 수 있다.
- 운전자는 실제로 운전 중이다. 과속, 급조작, 신호 위반, 화면 주시를 부추기는 대사는 쓰지 않는다. 급제동 반응은 나무라지 말고 안부를 먼저 묻는다. 부드럽게 운전할수록 이야기에 유리하게 만든다.
- 폭력적이거나 선정적인 내용, 실존 인물과 기존 작품의 캐릭터는 쓰지 않는다.
""".trim()

private fun spokenLines(s: Scenario): List<String> =
    with(s.persona.reactions) { listOf(harshAccel, harshBrake, harshTurn, congestion) } +
        s.chapters.flatMap { c ->
            listOf(c.opening, c.recap, c.cliffhanger, c.finale) +
                c.beats.flatMap { b -> listOf(b.line) + listOfNotNull(b.branch?.detour?.reply, b.branch?.keep?.reply, b.branch?.timeoutLine) }
        }

private fun extractJson(text: String): JSONObject? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return try {
        JSONObject(text.substring(start, end + 1))
    } catch (_: JSONException) {
        null
    }
}

class ScenarioWriter(private val llm: Llm) {
    fun write(idea: String, id: String): WriteResult {
        val prompt = idea.trim()
        if (prompt.isEmpty()) return WriteResult.Failed("아이디어를 한 줄 입력해 주세요.")
        if (prompt.length > IDEA_MAX) return WriteResult.Failed("아이디어는 ${IDEA_MAX}자 이하로 써 주세요.")
        val turns = mutableListOf(Turn(Role.USER, "아이디어: $prompt"))
        var lastIssues = emptyList<String>()
        repeat(SCENARIO_ATTEMPTS) { attempt ->
            val text = when (val r = llm.complete(SYSTEM, turns, Effort.MEDIUM, SCENARIO_MAX_TOKENS)) {
                is LlmResult.Text -> r.text
                LlmResult.Refused -> return WriteResult.Failed("이 아이디어로는 시나리오를 만들 수 없어요. 다른 아이디어를 써 주세요.")
                is LlmResult.Failed -> return WriteResult.Failed(r.reason)
            }
            val json = extractJson(text)
            val issues = when (val parsed = json?.let { parseScenarioJson(it, id, ScenarioSource.CUSTOM) }) {
                null -> listOf("JSON 객체 하나만 출력해야 합니다.")
                is DraftResult.Invalid -> parsed.issues
                is DraftResult.Ok -> {
                    val bad = unsafeLines(spokenLines(parsed.scenario))
                    if (bad.isEmpty()) return WriteResult.Ok(parsed.scenario, attempt + 1)
                    bad.map { "안전 운전에 어긋나는 대사입니다: \"$it\"" }
                }
            }
            lastIssues = issues
            turns += Turn(Role.ASSISTANT, text)
            turns += Turn(Role.USER, "아래 문제를 모두 고쳐서 JSON 전체를 다시 출력해 줘.\n" + issues.joinToString("\n") { "- $it" })
        }
        return WriteResult.Failed("AI가 규칙에 맞는 시나리오를 만들지 못했어요. (${lastIssues.firstOrNull().orEmpty()})")
    }
}
