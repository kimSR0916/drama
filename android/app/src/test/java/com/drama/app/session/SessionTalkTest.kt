package com.drama.app.session

import com.drama.app.authoring.AccountStore
import com.drama.app.authoring.MemoryKeyValue
import com.drama.app.core.Branch
import com.drama.app.core.DriveEvent
import com.drama.app.core.EmergencyCause
import com.drama.app.core.Gear
import com.drama.app.core.PreDriveEvent
import com.drama.app.core.RouteHint
import com.drama.app.core.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTalkTest {
    private val spoken = mutableListOf<String>()
    private var now = 0L

    private val outputs = object : Outputs {
        override fun speak(text: String) { spoken += text }
        override fun stopAllAudio() = Unit
        override fun bgm(on: Boolean) = Unit
        override fun reroute(prefer: RouteHint) = Unit
        override fun sos(cause: EmergencyCause) = Unit
        override fun listenForChoice(branch: Branch) = Unit
        override fun stopListening() = Unit
    }

    private fun driving(): Session {
        val store = AccountStore(MemoryKeyValue(), "t")
        store.putScenario(fixture)
        return Session(store, outputs, clock = { now }).apply {
            selectStory(fixture.id)
            pre(PreDriveEvent.StartNew(0))
            pre(PreDriveEvent.SetDestination(30.0))
            startDriving()
        }
    }

    @Test fun replyIsSpokenDuringTheDriveItBelongsTo() {
        val s = driving()
        val trip = s.tripStartedAt!!
        assertTrue(s.driverSaid("어디 가?"))
        s.characterReplied("은신처로 간다.", trip)
        assertEquals("은신처로 간다.", spoken.last())
        assertEquals(listOf("어디 가?", "은신처로 간다."), s.view.log.takeLast(2).map { it.text })
    }

    @Test fun lateReplyIsNotSpokenOverARouteQuestion() {
        val s = driving()
        val trip = s.tripStartedAt!!
        s.send(DriveEvent.Congestion(1))
        s.send(DriveEvent.Congestion(2))
        assertTrue(s.view.choices != null)
        assertFalse(s.driverSaid("잠깐만"))
        s.characterReplied("우회로가 좋겠군.", trip)
        assertFalse("우회로가 좋겠군." in spoken)
    }

    @Test fun replyFromAnEarlierDriveOrAfterParkingIsDropped() {
        val s = driving()
        val first = s.tripStartedAt!!
        s.send(DriveEvent.GearChanged(Gear.P, 10))
        s.characterReplied("늦은 대답", first)
        s.reset()
        now = 500
        s.selectStory(fixture.id)
        s.pre(PreDriveEvent.StartNew(500))
        s.pre(PreDriveEvent.SetDestination(30.0))
        s.startDriving()
        s.characterReplied("지난 주행의 대답", first)
        assertFalse(spoken.any { it == "늦은 대답" || it == "지난 주행의 대답" })
    }
}
