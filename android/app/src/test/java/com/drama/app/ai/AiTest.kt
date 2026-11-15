package com.drama.app.ai

import com.drama.app.core.ReactionKind
import com.drama.app.core.Slot
import com.drama.app.core.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AiTest {
    private class Scripted(vararg val replies: LlmResult) : Llm {
        val calls = mutableListOf<List<Turn>>()
        override fun complete(system: String, turns: List<Turn>, effort: Effort, maxTokens: Long): LlmResult {
            calls += turns.toList()
            return replies[calls.size - 1]
        }
    }

    private val valid = org.json.JSONArray(File("src/main/assets/scenarios.json").readText()).getJSONObject(1).toString()
    private val context = DriveContext(fixture, "제1장", emptyList(), 12, 1)

    @Test fun writerAcceptsAValidScenarioEvenWhenWrappedInProse() {
        val llm = Scripted(LlmResult.Text("여기 있습니다:\n```json\n$valid\n```"))
        val r = ScenarioWriter(llm).write("사라진 연구원을 쫓는 수사관", "custom-1") as WriteResult.Ok
        assertEquals("custom-1", r.scenario.id)
        assertEquals(1, r.attempts)
    }

    @Test fun writerSendsRuleViolationsBackAndRetries() {
        val tooLong = valid.replace("바로 출발하죠.", "바로 출발하죠." + "가".repeat(80))
        val llm = Scripted(LlmResult.Text(tooLong), LlmResult.Text(valid))
        val r = ScenarioWriter(llm).write("아이디어", "c") as WriteResult.Ok
        assertEquals(2, r.attempts)
        assertTrue(llm.calls[1].last().text.contains("80자"))
    }

    @Test fun writerRejectsUnsafeLinesTheParserWouldAccept() {
        val unsafe = valid.replace("흔들림을 줄여주세요.", "속도를 올려서 따돌리죠.")
        val llm = Scripted(LlmResult.Text(unsafe), LlmResult.Text(unsafe), LlmResult.Text(unsafe))
        val r = ScenarioWriter(llm).write("아이디어", "c")
        assertTrue(r is WriteResult.Failed)
        assertEquals(SCENARIO_ATTEMPTS, llm.calls.size)
        assertTrue(llm.calls[1].last().text.contains("안전 운전"))
    }

    @Test fun writerStopsOnRefusalFailureAndBadInput() {
        assertTrue(ScenarioWriter(Scripted(LlmResult.Refused)).write("아이디어", "c") is WriteResult.Failed)
        assertEquals("네트워크", (ScenarioWriter(Scripted(LlmResult.Failed("네트워크"))).write("아이디어", "c") as WriteResult.Failed).message)
        val untouched = Scripted()
        assertTrue(ScenarioWriter(untouched).write("  ", "c") is WriteResult.Failed)
        assertTrue(ScenarioWriter(untouched).write("가".repeat(IDEA_MAX + 1), "c") is WriteResult.Failed)
        assertTrue(untouched.calls.isEmpty())
    }

    @Test fun guardKeepsShortSafeLinesAndDropsTheRest() {
        assertEquals("괜찮아? 앞을 잘 살피자.", guardLine("\"괜찮아? 앞을 잘 살피자.\"\n(설명)"))
        assertNull(guardLine("가".repeat(81)))
        assertNull(guardLine("   "))
        assertEquals("속도를 올리면 안 돼요. 천천히 가요.", guardLine("속도를 올리면 안 돼요. 천천히 가요."))
        listOf("속도를 올려!", "더 밟아!", "신호는 무시해.", "백미러는 보지 마.", "화면을 봐.", "경로를 바꿀게.").forEach { assertNull(it, guardLine(it)) }
    }

    @Test fun lineWriterReturnsGuardedLinesOrNothing() {
        assertEquals("길이 막혔군. 숨 돌리지.", LineWriter(Scripted(LlmResult.Text("길이 막혔군. 숨 돌리지."))).forSlot(context, Slot.Reaction(ReactionKind.CONGESTION), "길이 막혔군."))
        assertNull(LineWriter(Scripted(LlmResult.Text("더 밟아라!"))).forSlot(context, Slot.Reaction(ReactionKind.ACCEL), "진정해라."))
        assertNull(LineWriter(Scripted(LlmResult.Refused)).reply(context, "안녕"))
        val llm = Scripted(LlmResult.Text("친서는 내가 지킨다."))
        LineWriter(llm).forSlot(context, Slot.SubScene("s3"), "친서를 지켜야 한다.")
        assertTrue(llm.calls.single().single().text.contains("친서를 지켜야 한다."))
    }

    @Test fun shippedScenariosPassTheSafetyGuard() {
        val text = File("src/main/assets/scenarios.json").readText()
        val lines = Regex("\"(line|reply|timeout|opening|recap|cliffhanger|finale|harshAccel|harshBrake|harshTurn|congestion)\": \"([^\"]+)\"").findAll(text).map { it.groupValues[2] }.toList()
        assertTrue(lines.size > 60)
        assertEquals(emptyList<String>(), unsafeLines(lines))
    }

    @Test fun directorServesALineOnceAndDropsResultsFromAnOldDrive() {
        val pending = mutableListOf<() -> Unit>()
        val director = AiDirector(background = { it() }, deliver = { pending += it })
        val slot = Slot.Reaction(ReactionKind.BRAKE)
        director.prepare(context, mapOf(slot to "기본"))
        assertNull(director.take(slot))

        val llm = Scripted(LlmResult.Text("첫 주행 대사"), LlmResult.Text("새 주행 대사"))
        director.writer = LineWriter(llm)
        director.prepare(context, mapOf(slot to "기본"))
        director.prepare(context, mapOf(slot to "기본"))
        assertEquals(1, llm.calls.size)
        director.reset()
        pending.removeAt(0)()
        assertNull(director.take(slot))

        director.prepare(context, mapOf(slot to "기본"))
        pending.removeAt(0)()
        assertEquals("새 주행 대사", director.take(slot))
        assertNull(director.take(slot))
    }
}
