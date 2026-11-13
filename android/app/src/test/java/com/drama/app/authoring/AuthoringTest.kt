package com.drama.app.authoring

import com.drama.app.core.Cursor
import com.drama.app.core.RouteHint
import com.drama.app.core.SaveData
import com.drama.app.core.ScenarioSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthoringTest {
    private val minimal = ScenarioDraft(
        title = "출근길 판타지", premise = "운전자는 용을 타고 출근하는 기사다.", personaName = "루나", personaTone = "장난스러운 요정",
        chapters = listOf(ChapterDraft("1장 출발", "루나: 출발!", listOf(BeatDraft("루나: 앞에 성이 보여요.")))),
    )

    @Test fun buildsPlayableCustomScenario() {
        val s = (parseScenarioDraft(minimal, "u1") as DraftResult.Ok).scenario
        assertEquals(ScenarioSource.CUSTOM, s.source)
        assertTrue("루나" in s.persona.reactions.harshBrake)
    }

    @Test fun reportsIssuesInKorean() {
        val r = parseScenarioDraft(ScenarioDraft("", "", "", "", listOf(ChapterDraft("", "", emptyList()))), "u1") as DraftResult.Invalid
        assertTrue(r.issues.containsAll(listOf("제목을(를) 입력해 주세요.", "1장에 장면을 하나 이상 넣어 주세요.")))
    }

    @Test fun detourMapsToRouteHint() {
        val d = minimal.copy(chapters = listOf(minimal.chapters[0].copy(beats = listOf(BeatDraft("갈림길", listOf(
            ChoiceDraft("왼쪽", "왼쪽!", detour = true), ChoiceDraft("오른쪽", "오른쪽!"),
        ))))))
        val s = (parseScenarioDraft(d, "u1") as DraftResult.Ok).scenario
        assertEquals(listOf(RouteHint.DETOUR, RouteHint.KEEP), s.chapters[0].beats[0].choices.map { it.routeHint })
    }

    @Test fun builtinsAreValidAndUnique() {
        assertTrue(BUILTIN_SCENARIOS.size >= 3)
        assertEquals(BUILTIN_SCENARIOS.size, BUILTIN_SCENARIOS.map { it.id }.toSet().size)
    }

    @Test fun storeRoundTripsAndIsolatesAccounts() {
        val kv = MemoryKeyValue()
        val a = AccountStore(kv, "alice")
        val s = BUILTIN_SCENARIOS[0]
        a.putScenario(s)
        a.putSave(SaveData(s.id, Cursor(0, 1), mapOf("x" to "y"), 1))
        assertEquals(s, a.getScenario(s.id))
        assertEquals(Cursor(0, 1), a.getSave(s.id)?.cursor)
        assertNull(AccountStore(kv, "bob").getSave(s.id))
        kv.set("alice/save/broken", "{oops")
        assertNull(a.getSave("broken"))
        a.deleteScenario(s.id)
        assertNull(a.getSave(s.id))
    }
}
