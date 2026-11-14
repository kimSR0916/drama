package com.drama.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MIN = 60_000L

class DriveTest {
    private fun begin(eta: Double, save: SaveData? = null): Step {
        val events = if (save == null) listOf(PreDriveEvent.SelectStory(fixture, null), PreDriveEvent.StartNew(0))
        else listOf(PreDriveEvent.SelectStory(fixture, save), PreDriveEvent.Answer(true, 0))
        val ready = (events + PreDriveEvent.SetDestination(eta)).fold(PreDriveState.Selecting as PreDriveState, ::preDrive) as PreDriveState.Ready
        return startDrive(ready, 0)
    }

    private fun feed(start: Step, vararg events: DriveEvent): Pair<DriveState, List<Effect>> {
        var state = start.state
        val effects = start.effects.toMutableList()
        for (e in events) {
            val step = drive(state, e)
            state = step.state
            effects += step.effects
        }
        return state to effects
    }

    private fun spoken(effects: List<Effect>) = effects.filterIsInstance<Effect.Speak>().map { it.text }
    private val question = "매복이다! 우회로로 빠질까, 직진할까?"
    private val spokenAt = 4_000L
    private val deadline = spokenAt + ANSWER_WINDOW_MS

    @Test fun shortTripAsksTheCoreQuestionAndShowsTwoChoices() {
        val (_, effects) = feed(begin(8.0), DriveEvent.Congestion(1000))
        assertTrue(question in spoken(effects))
        assertTrue(effects.any { it is Effect.ShowChoices })
    }

    @Test fun detourChoiceLogsTheDriverRepliesAndReroutes() {
        val (state, effects) = feed(begin(8.0), DriveEvent.Congestion(0), DriveEvent.DriverInput(RouteHint.DETOUR, InputVia.VOICE, 1000))
        assertEquals(listOf(Effect.Speak(Speaker.DRIVER, "우회로로 빠진다"), Effect.Speak(Speaker.CHARACTER, "현명한 판단이야!"), Effect.Reroute(RouteHint.DETOUR)), effects.takeLast(3))
        assertEquals("DETOUR", (state as DriveState.Driving).trip.flags["s2"])
    }

    @Test fun silenceForFiveSecondsAfterTheQuestionKeepsTheRouteWithTheBridgeLine() {
        val (_, early) = feed(begin(8.0), DriveEvent.Congestion(0), DriveEvent.QuestionSpoken(spokenAt), DriveEvent.Tick(deadline - 1, 7.0))
        assertFalse("답이 없군. 원래 길로 가지." in spoken(early))
        val (state, late) = feed(begin(8.0), DriveEvent.Congestion(0), DriveEvent.QuestionSpoken(spokenAt), DriveEvent.Tick(deadline, 7.0))
        assertTrue("답이 없군. 원래 길로 가지." in spoken(late))
        assertEquals(Effect.Reroute(RouteHint.KEEP), late.last())
        assertEquals(TIMEOUT_FLAG, (state as DriveState.Driving).trip.flags["s2"])
    }

    @Test fun theAnswerWindowWaitsForQueuedSpeechEvenIfItRunsLong() {
        val (state, effects) = feed(begin(8.0), DriveEvent.Congestion(0), DriveEvent.Tick(15_000, 7.0))
        assertFalse("답이 없군. 원래 길로 가지." in spoken(effects))
        assertTrue((state as DriveState.Driving).trip.pending != null)
        val (_, capped) = feed(begin(8.0), DriveEvent.Congestion(0), DriveEvent.Tick(speechMs(question) + QUESTION_SPEECH_CAP_MS, 7.0))
        assertTrue("답이 없군. 원래 길로 가지." in spoken(capped))
    }

    @Test fun parkingWithoutAnySceneEarnsNoBond() {
        val (state, _) = feed(begin(30.0), DriveEvent.GearChanged(Gear.P, 1000))
        assertEquals(0, (state as DriveState.Parked).report.bondAfter.points)
    }

    @Test fun reactionsHaveACooldownButEveryHarshEventCounts() {
        val (state, effects) = feed(
            begin(30.0),
            DriveEvent.HarshDriving(HarshKind.BRAKE, 1000),
            DriveEvent.HarshDriving(HarshKind.BRAKE, 2000),
            DriveEvent.HarshDriving(HarshKind.TURN, 40_000),
        )
        assertEquals(listOf("앞을 잘 살피라고!", "부드럽게 몰아라."), spoken(effects).drop(1))
        assertEquals(2, (state as DriveState.Driving).trip.harsh[HarshKind.BRAKE])
    }

    @Test fun shortTripSavesPastTheCoreSceneWithACliffhangerAndReportsOnPark() {
        val (state, effects) = feed(
            begin(8.0),
            DriveEvent.Congestion(0),
            DriveEvent.DriverInput(RouteHint.KEEP, InputVia.BUTTON, 1000),
            DriveEvent.Tick(7 * MIN, 1.0),
            DriveEvent.GearChanged(Gear.P, 8 * MIN),
        )
        assertTrue("다음에 계속." in spoken(effects))
        val save = effects.filterIsInstance<Effect.Save>().single().data
        assertEquals(Cursor(0, 2), save.cursor)
        assertEquals(BOND_PER_SCENE + BOND_PER_ANSWER + BOND_SAFE_BONUS, save.bond)
        val report = (state as DriveState.Parked).report
        assertEquals(0, report.progressBefore)
        assertEquals(40, report.progressAfter)
        assertEquals(RewardCard("성문 앞의 기사", CardTier.GOLD), report.reward)
        assertEquals(Effect.UxRestrictions(false), effects[effects.size - 2])
    }

    @Test fun finishingAChapterPlaysTheFinale() {
        val (_, effects) = feed(
            begin(30.0, SaveData("s1", Cursor(0, 3), emptyMap(), 0, 0)),
            DriveEvent.Congestion(0),
            DriveEvent.Tick(29 * MIN, 1.0),
        )
        assertTrue("1장 끝!" in spoken(effects))
        assertEquals(Cursor(1, 0), effects.filterIsInstance<Effect.Save>().single().data.cursor)
    }

    @Test fun arrivalWithAnOpenQuestionUsesTheBridgeLineFirst() {
        val (_, effects) = feed(begin(8.0), DriveEvent.Congestion(0), DriveEvent.Tick(1000, 1.0))
        val lines = spoken(effects)
        assertTrue(lines.indexOf("답이 없군. 원래 길로 가지.") < lines.indexOf("다음에 계속."))
    }

    @Test fun longTripSpreadsEverySceneBeforeArrivalWhenTicksFollowTheEta() {
        val ticks = (1..32).map { m -> DriveEvent.Tick(m * 1_000L + m * speechMs(question) + ANSWER_WINDOW_MS * m, 32.0 - m) }
        val (state, effects) = feed(begin(32.0), *ticks.toTypedArray())
        val lines = spoken(effects)
        listOf("잘 모는군.", question, "친서를 지켜야 한다.", "은신처가 보인다.").forEach { assertTrue(it, it in lines) }
        assertTrue(lines.indexOf("은신처가 보인다.") < lines.indexOf("1장 끝!"))
        assertTrue(state is DriveState.Arrived)
    }

    @Test fun tierFollowsSafetyScoreOnly() {
        assertEquals(CardTier.GOLD, cardTier(100))
        assertEquals(CardTier.SILVER, cardTier(92))
        assertEquals(CardTier.WOOD, cardTier(80))
        assertEquals(Bond(75, 2, "신뢰", 100), bondOf(fixture.persona, 75))
    }

    @Test fun sosMutesSavesAndLocksEvenAfterPark() {
        val (state, effects) = feed(
            begin(30.0),
            DriveEvent.Congestion(1000),
            DriveEvent.Emergency(EmergencyCause.AIRBAG, 2000),
            DriveEvent.GearChanged(Gear.P, 4000),
        )
        assertEquals(listOf(Effect.StopAllAudio, Effect.Sos(EmergencyCause.AIRBAG)), effects.takeLast(3).take(2))
        assertEquals(Cursor(0, 1), (effects.last() as Effect.Save).data.cursor)
        assertEquals(DriveState.Emergency(EmergencyCause.AIRBAG), state)
    }
}
