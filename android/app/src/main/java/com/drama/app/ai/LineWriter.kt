package com.drama.app.ai

import com.drama.app.authoring.LogEntry
import com.drama.app.core.ReactionKind
import com.drama.app.core.Scenario
import com.drama.app.core.Slot
import com.drama.app.core.Speaker

const val LINE_MAX_TOKENS = 2_000L
private const val CONTEXT_LINES = 8

data class DriveContext(
    val scenario: Scenario,
    val chapterTitle: String,
    val recent: List<LogEntry>,
    val etaMinutes: Int,
    val harshCount: Int,
)

private fun persona(c: DriveContext) = """
너는 차량용 인터랙티브 드라마의 등장인물 '${c.scenario.persona.name}'이다. 말투: ${c.scenario.persona.tone}.
세계관: ${c.scenario.premise}
지금 운전자는 실제로 운전 중이고, 네 대사는 음성으로만 전달된다.

규칙:
- 대사 한 줄만 출력한다. 따옴표, 이름표, 설명, 줄바꿈 없이 대사 본문만 쓴다.
- 공백 포함 80자 이하의 한국어로 쓴다.
- 운전자에게 화면을 보라고 하거나, 속도를 올리거나, 급하게 움직이도록 부추기지 않는다. 침착하고 안전한 운전을 응원한다.
- 경로 변경이나 우회를 스스로 제안하거나 약속하지 않는다. 경로 선택은 정해진 장면에서만 다룬다.
- 세계관과 말투를 유지한다.
""".trim()

private fun history(c: DriveContext): String {
    val name = c.scenario.persona.name
    val lines = c.recent.takeLast(CONTEXT_LINES).joinToString("\n") {
        when (it.speaker) {
            Speaker.DRIVER -> "운전자: ${it.text}"
            Speaker.NARRATOR -> "내레이션: ${it.text}"
            Speaker.CHARACTER -> "$name: ${it.text}"
        }
    }
    return "챕터: ${c.chapterTitle}\n도착까지 약 ${c.etaMinutes}분, 이번 주행의 급조작 ${c.harshCount}회.\n최근 대화:\n${lines.ifEmpty { "(아직 없음)" }}"
}

private fun situation(kind: ReactionKind) = when (kind) {
    ReactionKind.ACCEL -> "운전자가 방금 급가속을 했다. 이야기 속 상황에 빗대어 부드럽게 달래라."
    ReactionKind.BRAKE -> "운전자가 방금 급제동을 했다. 괜찮은지 먼저 살피고, 나무라지 말고 앞을 미리 살피자고 권해라."
    ReactionKind.TURN -> "운전자가 방금 거칠게 방향을 틀었다. 이야기 속 상황에 빗대어 부드러운 조향을 권해라."
    ReactionKind.CONGESTION -> "도로가 막혀 천천히 가고 있다. 이야기 속 이유를 붙여 초조해하지 말자고 말해라."
}

class LineWriter(private val llm: Llm) {
    private fun one(c: DriveContext, ask: String): String? =
        when (val r = llm.complete(persona(c), listOf(Turn(Role.USER, "${history(c)}\n\n$ask")), Effort.LOW, LINE_MAX_TOKENS)) {
            is LlmResult.Text -> guardLine(r.text)
            else -> null
        }

    fun forSlot(c: DriveContext, slot: Slot, scripted: String): String? = when (slot) {
        is Slot.SubScene -> one(c, "다음 장면에서 전하려는 내용은 이렇다: \"$scripted\"\n이 내용과 줄거리는 그대로 지키되, 위 대화와 주행 상황을 반영해 네 말로 다시 써라.")
        is Slot.Reaction -> one(c, "${situation(slot.kind)}\n참고용 기본 대사: \"$scripted\"")
    }

    fun reply(c: DriveContext, utterance: String): String? =
        one(c, "운전자가 방금 너에게 이렇게 말했다: \"$utterance\"\n등장인물로서 짧게 대답해라. 이야기의 결말이나 아직 나오지 않은 사건은 밝히지 않는다.")
}
