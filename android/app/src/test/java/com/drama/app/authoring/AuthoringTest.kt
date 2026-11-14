package com.drama.app.authoring

import com.drama.app.core.Cursor
import com.drama.app.core.SaveData
import com.drama.app.core.ScenarioSource
import com.drama.app.core.SceneKind
import com.drama.app.core.Speaker
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AuthoringTest {
    private val builtins by lazy { parseBuiltins(File("src/main/assets/scenarios.json").readText()) }

    private fun scene(line: String, keyword: String = "우회") = JSONObject(
        """{"core":true,"line":"$line","detour":{"label":"A","keyword":"$keyword","reply":"좋아"},"keep":{"label":"B","keyword":"직진","reply":"그래"},"timeout":"원래 길로"}""",
    )

    private fun draft(vararg scenes: JSONObject) = JSONObject()
        .put("title", "t").put("premise", "p")
        .put("persona", JSONObject().put("name", "n").put("tone", "t"))
        .put("chapters", org.json.JSONArray().put(JSONObject().put("title", "1장").put("opening", "출발").put("scenes", org.json.JSONArray(scenes.toList()))))

    @Test fun shippedScenariosFollowThePlanningRules() {
        assertTrue(builtins.size >= 2)
        assertEquals(builtins.size, builtins.map { it.id }.toSet().size)
        builtins.flatMap { it.chapters }.forEach { c -> assertTrue(c.title, c.beats.any { it.kind == SceneKind.CORE }) }
    }

    @Test fun rejectsSpokenLinesOverEightyCharacters() {
        val r = parseScenarioJson(draft(JSONObject().put("line", "가".repeat(81)).put("core", true)), "x", ScenarioSource.CUSTOM) as DraftResult.Invalid
        assertTrue(r.issues.single().contains("80자"))
    }

    @Test fun requiresBothChoiceKeywordsInTheQuestion() {
        val r = parseScenarioJson(draft(scene("우회할까요, 그대로 갈까요?")), "x", ScenarioSource.CUSTOM) as DraftResult.Invalid
        assertTrue(r.issues.single().contains("'직진'"))
        assertTrue(parseScenarioJson(draft(scene("우회할까요, 직진할까요?")), "x", ScenarioSource.CUSTOM) is DraftResult.Ok)
    }

    @Test fun requiresACoreSceneInEveryChapter() {
        val r = parseScenarioJson(draft(JSONObject().put("line", "안녕").put("core", false)), "x", ScenarioSource.CUSTOM) as DraftResult.Invalid
        assertTrue(r.issues.single().contains("핵심(Core)"))
    }

    @Test fun customDraftGetsDefaultsAndRoundTripsThroughTheStore() {
        val s = (parseScenarioJson(customDraftJson("제목", "세계", "루나", "요정", "1장", "출발!", listOf("안녕", "또 만나")), "u1", ScenarioSource.CUSTOM) as DraftResult.Ok).scenario
        assertEquals(SceneKind.CORE, s.chapters[0].beats[0].kind)
        val kv = MemoryKeyValue()
        val store = AccountStore(kv, "alice")
        listOf(s, builtins[0]).forEach { store.putScenario(it); assertEquals(it, store.getScenario(it.id)) }
    }

    @Test fun storeKeepsSavesAndLogsPerAccount() {
        val kv = MemoryKeyValue()
        val a = AccountStore(kv, "alice")
        a.putSave(SaveData("s", Cursor(0, 1), mapOf("x" to "y"), 30, 1))
        a.putLog("s", listOf(LogEntry(Speaker.DRIVER, "우회로로 빠진다")))
        assertEquals(30, a.getSave("s")?.bond)
        assertEquals(Speaker.DRIVER, a.getLog("s").single().speaker)
        assertNull(AccountStore(kv, "bob").getSave("s"))
        kv.set("alice/save/broken", "{oops")
        assertNull(a.getSave("broken"))
        a.deleteScenario("s")
        assertTrue(a.getLog("s").isEmpty())
    }
}
